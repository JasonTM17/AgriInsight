package com.agriinsight.backend.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.integration.application.OperationalEventPublisher;
import com.agriinsight.backend.integration.application.OutboxPublishingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RealtimePublisherConfigurationTest {

    @Test
    void disabledPublisherCreatesNoWorkerBeansOrBrokerConnectionBoundary() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        RealtimePublisherConfiguration.class,
                        OutboxPublishingSchedule.class,
                        PostgresOutboxStore.class)
                .withPropertyValues("agriinsight.realtime.publisher-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RealtimeWorkerProperties.class);
                    assertThat(context).doesNotHaveBean(OperationalEventPublisher.class);
                    assertThat(context).doesNotHaveBean(OutboxPublishingService.class);
                    assertThat(context).doesNotHaveBean(OutboxPublishingSchedule.class);
                    assertThat(context).doesNotHaveBean(PostgresOutboxStore.class);
                });
    }
}
