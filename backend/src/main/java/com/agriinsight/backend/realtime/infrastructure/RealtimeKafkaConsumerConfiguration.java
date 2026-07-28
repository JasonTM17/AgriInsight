package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEventParser;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore;
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
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RealtimeWorkerProperties.class)
@ConditionalOnProperty(
        prefix = "agriinsight.realtime",
        name = "consumer-enabled",
        havingValue = "true")
public class RealtimeKafkaConsumerConfiguration {

    private static final long RETRY_INTERVAL_MILLIS = 500;
    private static final long RETRY_ATTEMPTS = 2;

    @Bean
    ProducerFactory<byte[], byte[]> realtimeDeadLetterProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> configuration = new HashMap<>(kafkaProperties.buildProducerProperties());
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(configuration);
    }

    @Bean
    RealtimeOperationalEventParser realtimeOperationalEventParser(JsonMapper jsonMapper) {
        return new RealtimeOperationalEventParser(jsonMapper);
    }

    @Bean
    RealtimeEventIngestionService realtimeEventIngestionService(
            RealtimeReadModelStore store,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setName("realtime-read-model");
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new RealtimeEventIngestionService(store, transaction);
    }

    @Bean
    DefaultErrorHandler realtimeKafkaErrorHandler(
            @Qualifier("realtimeDeadLetterProducerFactory") ProducerFactory<byte[], byte[]> producerFactory,
            RealtimeWorkerProperties properties) {
        return errorHandler(new KafkaTemplate<>(producerFactory), properties);
    }

    static DefaultErrorHandler errorHandler(
            KafkaOperations<byte[], byte[]> kafkaOperations,
            RealtimeWorkerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = deadLetterRecoverer(kafkaOperations, properties);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, RETRY_ATTEMPTS));
        errorHandler.setAckAfterHandle(true);
        return errorHandler;
    }

    static DeadLetterPublishingRecoverer deadLetterRecoverer(
            KafkaOperations<byte[], byte[]> kafkaOperations,
            RealtimeWorkerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(properties.sendTimeout());
        return recoverer;
    }
}
