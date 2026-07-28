package com.agriinsight.backend.integration.infrastructure;

import com.agriinsight.backend.integration.application.OperationalEventPublisher;
import com.agriinsight.backend.integration.application.OutboxDrainService;
import com.agriinsight.backend.integration.application.OutboxPublishingService;
import com.agriinsight.backend.integration.application.OutboxStore;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RealtimeWorkerProperties.class)
@ConditionalOnProperty(
        prefix = "agriinsight.realtime",
        name = "publisher-enabled",
        havingValue = "true")
public class RealtimePublisherConfiguration {

    @Bean
    Clock realtimeClock() {
        return Clock.systemUTC();
    }

    @Bean
    OutboxDrainService outboxDrainService(OutboxStore outboxStore) {
        return new OutboxDrainService(outboxStore);
    }

    @Bean
    ProducerFactory<String, String> realtimeOperationalEventProducerFactory(
            KafkaProperties kafkaProperties,
            RealtimeWorkerProperties properties) {
        Map<String, Object> configuration = new HashMap<>(kafkaProperties.buildProducerProperties());
        // Kafka can block before returning a send future while it fetches topic metadata.
        configuration.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, properties.sendTimeout().toMillis());
        return new DefaultKafkaProducerFactory<>(configuration);
    }

    @Bean
    KafkaTemplate<String, String> realtimeOperationalEventKafkaTemplate(
            @Qualifier("realtimeOperationalEventProducerFactory")
                    ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    OperationalEventPublisher operationalEventPublisher(
            @Qualifier("realtimeOperationalEventKafkaTemplate")
                    KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            RealtimeWorkerProperties properties) {
        return new KafkaOperationalEventPublisher(kafkaTemplate, jsonMapper, properties);
    }

    @Bean
    OutboxPublishingService outboxPublishingService(
            OutboxDrainService drainService,
            OperationalEventPublisher publisher,
            Clock realtimeClock,
            RealtimeWorkerProperties properties) {
        return new OutboxPublishingService(
                drainService,
                publisher,
                realtimeClock,
                properties.workerId(),
                properties.batchSize(),
                properties.leaseDuration());
    }

    @Bean
    NewTopic operationalEventsTopic(RealtimeWorkerProperties properties) {
        return topic(
                properties.topic(),
                properties.partitions(),
                properties.replicationFactor(),
                properties.maxRecordBytes());
    }

    @Bean
    NewTopic operationalEventsDeadLetterTopic(RealtimeWorkerProperties properties) {
        return topic(
                properties.deadLetterTopic(),
                properties.partitions(),
                properties.replicationFactor(),
                properties.maxRecordBytes());
    }

    private static NewTopic topic(
            String name, int partitions, short replicationFactor, int maxRecordBytes) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .configs(Map.of("max.message.bytes", Integer.toString(maxRecordBytes)))
                .build();
    }
}
