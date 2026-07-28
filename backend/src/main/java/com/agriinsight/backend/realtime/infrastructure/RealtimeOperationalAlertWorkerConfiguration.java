package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeDeadLetterEnvelopeValidator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertStore;
import java.time.Clock;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
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
            PlatformTransactionManager transactionManager,
            @Qualifier("realtimeAlertClock") Clock clock,
            RealtimeAlertWorkerProperties properties) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setName("realtime-operational-alerts");
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return new RealtimeOperationalAlertEvaluator(store, transaction, clock, properties);
    }

    @Bean("realtimeAlertWorkerRoleVerifier")
    RealtimeWorkerRoleVerifier realtimeAlertWorkerRoleVerifier(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        RealtimeWorkerRoleVerifier verifier =
                new RealtimeWorkerRoleVerifier(jdbcTemplate, workerProperties, alertProperties);
        verifier.verify();
        return verifier;
    }

    @Bean
    RealtimeDeadLetterEnvelopeValidator realtimeDeadLetterEnvelopeValidator(JsonMapper jsonMapper) {
        return new RealtimeDeadLetterEnvelopeValidator(jsonMapper);
    }

    @Bean
    DefaultErrorHandler realtimeDeadLetterAlertErrorHandler(
            @Qualifier("realtimeDeadLetterKafkaTemplate") KafkaTemplate<byte[], byte[]> kafkaTemplate,
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
    org.apache.kafka.clients.admin.NewTopic realtimeAlertObserverFailureTopic(
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties) {
        return TopicBuilder.name(alertProperties.observerFailureTopic())
                .partitions(workerProperties.partitions())
                .replicas(workerProperties.replicationFactor())
                .configs(Map.of(
                        "max.message.bytes", Integer.toString(workerProperties.maxRecordBytes())))
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
