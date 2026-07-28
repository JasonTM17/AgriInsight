package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.agriinsight.backend.integration.application.OutboxStore;
import com.agriinsight.backend.integration.infrastructure.RealtimePublisherConfiguration;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class RealtimeKafkaWorkerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(
                    RealtimePublisherConfiguration.class,
                    RealtimeKafkaConsumerConfiguration.class,
                    WorkerDependencies.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=127.0.0.1:9092",
                    "agriinsight.realtime.publisher-enabled=true",
                    "agriinsight.realtime.consumer-enabled=true",
                    "agriinsight.realtime.worker-id=configuration-test-worker",
                    "agriinsight.realtime.batch-size=10",
                    "agriinsight.realtime.lease-duration=30s",
                    "agriinsight.realtime.poll-delay=1s",
                    "agriinsight.realtime.send-timeout=2s",
                    "agriinsight.realtime.topic=agriinsight.configuration-test",
                    "agriinsight.realtime.dead-letter-topic=agriinsight.configuration-test.dlt",
                    "agriinsight.realtime.partitions=1",
                    "agriinsight.realtime.replication-factor=1",
                    "agriinsight.realtime.max-record-bytes=262144");

    @Test
    void usesSeparateManagedTemplatesForOperationalEventsAndDeadLetters() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("realtimeKafkaErrorHandler");
            assertThat(context.getBeansOfType(KafkaTemplate.class))
                    .containsKeys(
                            "realtimeOperationalEventKafkaTemplate",
                            "realtimeDeadLetterKafkaTemplate")
                    .hasSize(2);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class WorkerDependencies {

        @Bean
        JsonMapper jsonMapper() {
            return new JsonMapper();
        }

        @Bean
        OutboxStore outboxStore() {
            return mock(OutboxStore.class);
        }

        @Bean
        RealtimeReadModelStore realtimeReadModelStore() {
            return mock(RealtimeReadModelStore.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        KafkaAdmin kafkaAdmin() {
            return mock(KafkaAdmin.class);
        }
    }
}
