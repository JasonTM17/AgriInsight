package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.AgriInsightBackendApplication;
import com.agriinsight.backend.integration.infrastructure.OutboxPublishingSchedule;
import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Starts the production realtime worker wiring against the E2E PostgreSQL and Kafka containers. */
final class RealtimeWorkerE2eHarness implements AutoCloseable {

    private final ConfigurableApplicationContext context;
    private final OutboxPublishingSchedule publisher;
    private final KafkaListenerEndpointRegistry listeners;

    private RealtimeWorkerE2eHarness(
            ConfigurableApplicationContext context,
            OutboxPublishingSchedule publisher,
            KafkaListenerEndpointRegistry listeners) {
        this.context = context;
        this.publisher = publisher;
        this.listeners = listeners;
    }

    static RealtimeWorkerE2eHarness start(
            PostgreSQLContainer postgresql,
            String bootstrapServers) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(AgriInsightBackendApplication.class)
                .web(WebApplicationType.NONE)
                .run(commandLineArguments(workerProperties(postgresql, bootstrapServers)));
        KafkaListenerEndpointRegistry listeners = context.getBean(KafkaListenerEndpointRegistry.class);
        requireConsumerWiring(context, listeners);
        return new RealtimeWorkerE2eHarness(
                context,
                context.getBean(OutboxPublishingSchedule.class),
                listeners);
    }

    void publishAvailable() {
        publisher.publishAvailable();
    }

    void stopConsumers() throws Throwable {
        listeners.stop();
        RealtimeKafkaE2eSupport.await("Kafka listener stop", Duration.ofSeconds(10),
                () -> listenerContainers().stream().noneMatch(MessageListenerContainer::isRunning));
    }

    void startConsumers() throws Throwable {
        listeners.start();
        RealtimeKafkaE2eSupport.await("Kafka listener start", Duration.ofSeconds(10),
                () -> listenerContainers().stream().allMatch(MessageListenerContainer::isRunning));
    }

    @Override
    public void close() {
        context.close();
    }

    private static String[] workerProperties(PostgreSQLContainer postgresql, String bootstrapServers) {
        return new String[] {
            "spring.main.banner-mode=off",
            "spring.datasource.url=" + PostgresIntegrationSupport.jdbcUrl(postgresql, "agriinsight"),
            "spring.datasource.username=" + PostgresIntegrationSupport.REALTIME,
            "spring.datasource.password=" + PostgresIntegrationSupport.REALTIME_PASSWORD,
            "spring.flyway.enabled=false",
            "spring.kafka.bootstrap-servers=" + bootstrapServers,
            "spring.kafka.consumer.group-id=" + RealtimeKafkaE2eSupport.CONSUMER_GROUP,
            "agriinsight.identity.enabled=false",
            "agriinsight.realtime.publisher-enabled=true",
            "agriinsight.realtime.consumer-enabled=true",
            "agriinsight.realtime.worker-id=realtime-e2e-worker",
            "agriinsight.realtime.batch-size=10",
            "agriinsight.realtime.lease-duration=30s",
            "agriinsight.realtime.poll-delay=1s",
            "agriinsight.realtime.send-timeout=2s",
            "agriinsight.realtime.topic=" + RealtimeKafkaE2eSupport.TOPIC,
            "agriinsight.realtime.dead-letter-topic=" + RealtimeKafkaE2eSupport.DEAD_LETTER_TOPIC,
            "agriinsight.realtime.partitions=1",
            "agriinsight.realtime.replication-factor=1",
            "agriinsight.realtime.max-record-bytes=262144"
        };
    }

    private static String[] commandLineArguments(String[] properties) {
        return Arrays.stream(properties)
                .map(property -> "--" + property)
                .toArray(String[]::new);
    }

    private static void requireConsumerWiring(
            ConfigurableApplicationContext context,
            KafkaListenerEndpointRegistry listeners) {
        context.getBean(KafkaRealtimeOperationalEventConsumer.class);
        context.getBean("realtimeKafkaErrorHandler", DefaultErrorHandler.class);
        if (listeners.getListenerContainers().isEmpty()) {
            throw new IllegalStateException("Production Kafka listener was not registered");
        }
    }

    private List<MessageListenerContainer> listenerContainers() {
        return List.copyOf(listeners.getListenerContainers());
    }
}
