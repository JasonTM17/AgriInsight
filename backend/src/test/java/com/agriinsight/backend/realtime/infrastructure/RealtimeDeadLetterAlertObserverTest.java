package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeDeadLetterEnvelopeValidator;
import com.agriinsight.backend.realtime.application.RealtimeEventValidationException;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class RealtimeDeadLetterAlertObserverTest {

    @Test
    void commitsUnattributableDltRecordsWithoutCreatingATenantAlert() {
        RealtimeDeadLetterEnvelopeValidator validator = mock(RealtimeDeadLetterEnvelopeValidator.class);
        RealtimeOperationalAlertEvaluator evaluator = mock(RealtimeOperationalAlertEvaluator.class);
        ConsumerRecord<byte[], byte[]> record = record();
        when(validator.parse(record, 262_144)).thenThrow(new RealtimeEventValidationException("invalid"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RealtimeDeadLetterAlertObserver observer =
                new RealtimeDeadLetterAlertObserver(validator, evaluator, properties(), registry);

        observer.observe(record);

        verify(evaluator, never()).observeDeadLetter(org.mockito.ArgumentMatchers.any());
        assertThat(registry.get("agriinsight.realtime.alerts.dlt.unattributable")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    void propagatesStorageFailuresToTheDedicatedKafkaErrorHandler() {
        RealtimeDeadLetterEnvelopeValidator validator = mock(RealtimeDeadLetterEnvelopeValidator.class);
        RealtimeOperationalAlertEvaluator evaluator = mock(RealtimeOperationalAlertEvaluator.class);
        ConsumerRecord<byte[], byte[]> record = record();
        RealtimeOperationalEvent event = event();
        when(validator.parse(record, 262_144)).thenReturn(event);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(evaluator)
                .observeDeadLetter(event);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RealtimeDeadLetterAlertObserver observer =
                new RealtimeDeadLetterAlertObserver(validator, evaluator, properties(), registry);

        assertThatThrownBy(() -> observer.observe(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private static ConsumerRecord<byte[], byte[]> record() {
        return new ConsumerRecord<>(
                "agriinsight.operational.v1.dlt", 1, 4, new byte[] {1}, new byte[] {2});
    }

    private static RealtimeOperationalEvent event() {
        return new RealtimeOperationalEvent(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                Instant.parse("2027-09-01T00:00:00Z"),
                "a".repeat(64),
                "agriinsight.operational.v1.dlt",
                1,
                4);
    }

    private static RealtimeWorkerProperties properties() {
        return new RealtimeWorkerProperties(
                false,
                true,
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                "agriinsight.operational.v1",
                "agriinsight.operational.v1.dlt",
                3,
                (short) 1,
                262_144);
    }
}
