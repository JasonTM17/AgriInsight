package com.agriinsight.backend.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RealtimeWorkerPropertiesTest {

    @Test
    void acceptsBoundedExplicitWorkerConfiguration() {
        RealtimeWorkerProperties properties = validProperties();

        assertThat(properties.publisherEnabled()).isFalse();
        assertThat(properties.consumerEnabled()).isFalse();
        assertThat(properties.topic()).isEqualTo("agriinsight.operational.v1");
        assertThat(properties.deadLetterTopic()).isEqualTo("agriinsight.operational.v1.dlt");
    }

    @Test
    void rejectsUnsafeTopicsAndUnboundedWork() {
        assertThatThrownBy(() -> properties(
                        "agriinsight.operational.v1",
                        "agriinsight.operational.v1",
                        20,
                        Duration.ofSeconds(30),
                        262_144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct");
        assertThatThrownBy(() -> properties(
                        "bad topic",
                        "agriinsight.operational.v1.dlt",
                        20,
                        Duration.ofSeconds(30),
                        262_144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> properties(
                        "agriinsight.operational.v1",
                        "agriinsight.operational.v1.dlt",
                        101,
                        Duration.ofSeconds(30),
                        262_144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
        assertThatThrownBy(() -> properties(
                        "agriinsight.operational.v1",
                        "agriinsight.operational.v1.dlt",
                        20,
                        Duration.ofMinutes(16),
                        262_144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease-duration");
        assertThatThrownBy(() -> properties(
                        "agriinsight.operational.v1",
                        "agriinsight.operational.v1.dlt",
                        20,
                        Duration.ofSeconds(30),
                        2_000_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-record-bytes");
    }

    private static RealtimeWorkerProperties validProperties() {
        return properties(
                "agriinsight.operational.v1",
                "agriinsight.operational.v1.dlt",
                20,
                Duration.ofSeconds(30),
                262_144);
    }

    private static RealtimeWorkerProperties properties(
            String topic,
            String deadLetterTopic,
            int batchSize,
            Duration leaseDuration,
            int maxRecordBytes) {
        return new RealtimeWorkerProperties(
                false,
                false,
                "realtime-worker-1",
                batchSize,
                leaseDuration,
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                topic,
                deadLetterTopic,
                6,
                (short) 1,
                maxRecordBytes);
    }
}
