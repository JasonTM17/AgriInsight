package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeDeadLetterEnvelopeValidator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

/** Wires the non-web operational alert worker without changing the existing v1 consumer. */
@Configuration(proxyBeanMethods = false)
@Profile("realtime-worker")
@EnableScheduling
@EnableConfigurationProperties({RealtimeWorkerProperties.class, RealtimeAlertWorkerProperties.class})
@ConditionalOnProperty(
        prefix = "agriinsight.realtime.alerts",
        name = "enabled",
        havingValue = "true")
public class RealtimeOperationalAlertWorkerConfiguration {

    @Bean
    Clock realtimeAlertClock() {
        return Clock.systemUTC();
    }

    @Bean
    RealtimeOperationalAlertEvaluator realtimeOperationalAlertEvaluator(
            RealtimeOperationalAlertStore store,
            RealtimeOperationalAlertScanStore scanStore,
            PlatformTransactionManager transactionManager,
            @Qualifier("realtimeAlertClock") Clock clock,
            RealtimeAlertWorkerProperties properties,
            MeterRegistry meterRegistry) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setName("realtime-operational-alerts");
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(Math.toIntExact(properties.maximumQueryDuration().toSeconds()));
        TransactionTemplate deadLetterTransaction = new TransactionTemplate(transactionManager);
        deadLetterTransaction.setName("realtime-operational-alert-dlt");
        deadLetterTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        deadLetterTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        deadLetterTransaction.setTimeout(Math.toIntExact(properties.maximumQueryDuration().toSeconds()));
        return new RealtimeOperationalAlertEvaluator(
                store, scanStore, transaction, deadLetterTransaction, clock, properties, meterRegistry);
    }

    @Bean("realtimeAlertWorkerRoleVerifier")
    RealtimeWorkerRoleVerifier realtimeAlertWorkerRoleVerifier(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties,
            KafkaProperties kafkaProperties) {
        RealtimeWorkerRoleVerifier verifier =
                new RealtimeWorkerRoleVerifier(
                        jdbcTemplate, workerProperties, alertProperties, kafkaProperties);
        verifier.verify();
        return verifier;
    }

    @Bean
    RealtimeDeadLetterEnvelopeValidator realtimeDeadLetterEnvelopeValidator(JsonMapper jsonMapper) {
        return new RealtimeDeadLetterEnvelopeValidator(jsonMapper);
    }

    @Bean
    ProducerFactory<byte[], byte[]> realtimeAlertObserverProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> configuration = new HashMap<>(kafkaProperties.buildProducerProperties());
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(configuration);
    }

    @Bean("realtimeAlertObserverKafkaTemplate")
    KafkaTemplate<byte[], byte[]> realtimeAlertObserverKafkaTemplate(
            @Qualifier("realtimeAlertObserverProducerFactory")
                    ProducerFactory<byte[], byte[]> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    DefaultErrorHandler realtimeDeadLetterAlertErrorHandler(
            @Qualifier("realtimeAlertObserverKafkaTemplate") KafkaTemplate<byte[], byte[]> kafkaTemplate,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        return observerErrorHandler(kafkaTemplate, workerProperties, alertProperties);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<byte[], byte[]>
            realtimeDeadLetterAlertKafkaListenerContainerFactory(
                    ConsumerFactory<byte[], byte[]> consumerFactory,
                    @Qualifier("realtimeDeadLetterAlertErrorHandler")
                            DefaultErrorHandler realtimeDeadLetterAlertErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(realtimeDeadLetterAlertErrorHandler);
        return factory;
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic realtimeObservedDeadLetterTopic(
            RealtimeWorkerProperties workerProperties) {
        return topic(
                workerProperties.deadLetterTopic(),
                workerProperties.partitions(),
                workerProperties.replicationFactor(),
                workerProperties.maxRecordBytes());
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic realtimeAlertObserverFailureTopic(
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        return topic(
                alertProperties.observerFailureTopic(),
                workerProperties.partitions(),
                workerProperties.replicationFactor(),
                workerProperties.maxRecordBytes());
    }

    private static org.apache.kafka.clients.admin.NewTopic topic(
            String name, int partitions, short replicationFactor, int maxRecordBytes) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .configs(Map.of("max.message.bytes", Integer.toString(maxRecordBytes)))
                .build();
    }

    static DefaultErrorHandler observerErrorHandler(
            KafkaOperations<byte[], byte[]> kafkaOperations,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        DeadLetterPublishingRecoverer recoverer = observerFailureRecoverer(
                kafkaOperations, workerProperties, alertProperties);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        alertProperties.observerRetryInterval().toMillis(),
                        alertProperties.observerFailureRetries()));
        handler.setAckAfterHandle(true);
        return handler;
    }

    static DeadLetterPublishingRecoverer observerFailureRecoverer(
            KafkaOperations<byte[], byte[]> kafkaOperations,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new TopicPartition(
                        alertProperties.observerFailureTopic(), record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(workerProperties.sendTimeout());
        return recoverer;
    }
}
