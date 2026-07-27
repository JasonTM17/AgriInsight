package com.agriinsight.backend.integration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxPublishingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T14:00:00Z");

    @Test
    void acknowledgesOnlyAfterThePublisherReturnsSuccessfully() {
        RecordingStore store = new RecordingStore();
        OutboxPublishingService service = service(store, ignored -> store.publishReturned = true);

        OutboxPublishingService.PublishResult result = service.publishAvailable();

        assertThat(result).isEqualTo(new OutboxPublishingService.PublishResult(1, 1, 0, 0, 0));
        assertThat(store.acknowledged).isTrue();
        assertThat(store.publishReturnedBeforeAcknowledge).isTrue();
        assertThat(store.failed).isFalse();
    }

    @Test
    void requeuesAConfirmedPublisherFailureWithoutAcknowledging() {
        RecordingStore store = new RecordingStore();
        OutboxPublishingService service = service(store, ignored -> {
            throw new IllegalStateException("broker detail must not be persisted");
        });

        OutboxPublishingService.PublishResult result = service.publishAvailable();

        assertThat(result).isEqualTo(new OutboxPublishingService.PublishResult(1, 0, 1, 0, 0));
        assertThat(store.acknowledged).isFalse();
        assertThat(store.failed).isTrue();
        assertThat(store.lastError).isEqualTo("Kafka publication failed: IllegalStateException");
    }

    @Test
    void reportsAStaleLeaseWithoutConvertingItToSuccess() {
        RecordingStore store = new RecordingStore();
        store.acknowledgeResult = false;
        OutboxPublishingService service = service(store, ignored -> store.publishReturned = true);

        OutboxPublishingService.PublishResult result = service.publishAvailable();

        assertThat(result).isEqualTo(new OutboxPublishingService.PublishResult(1, 0, 0, 0, 1));
        assertThat(store.failed).isFalse();
    }

    @Test
    void reportsADeadLetterWhenTheAttemptBudgetIsExhausted() {
        RecordingStore store = new RecordingStore();
        store.failureResult = OutboxDrainService.FailureResult.DEAD_LETTER;
        OutboxPublishingService service = service(store, ignored -> {
            throw new IllegalStateException("unavailable");
        });

        OutboxPublishingService.PublishResult result = service.publishAvailable();

        assertThat(result).isEqualTo(new OutboxPublishingService.PublishResult(1, 0, 0, 1, 0));
        assertThat(store.acknowledged).isFalse();
    }

    @Test
    void reportsAStaleLeaseWhenFailureStateCannotBePersisted() {
        RecordingStore store = new RecordingStore();
        store.failureResult = OutboxDrainService.FailureResult.STALE;
        OutboxPublishingService service = service(store, ignored -> {
            throw new IllegalStateException("unavailable");
        });

        OutboxPublishingService.PublishResult result = service.publishAvailable();

        assertThat(result).isEqualTo(new OutboxPublishingService.PublishResult(1, 0, 0, 0, 1));
        assertThat(store.acknowledged).isFalse();
    }

    private static OutboxPublishingService service(
            RecordingStore store, OperationalEventPublisher publisher) {
        return new OutboxPublishingService(
                new OutboxDrainService(store),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30));
    }

    private static final class RecordingStore implements OutboxStore {

        private final OutboxEvent event = event();
        private boolean acknowledged;
        private boolean failed;
        private boolean publishReturned;
        private boolean publishReturnedBeforeAcknowledge;
        private boolean acknowledgeResult = true;
        private OutboxDrainService.FailureResult failureResult =
                OutboxDrainService.FailureResult.REQUEUED;
        private String lastError;

        @Override
        public List<OutboxDrainService.OutboxLease> lease(
                String owner, int limit, Duration leaseDuration, Instant now) {
            return List.of(new OutboxDrainService.OutboxLease(event, owner, UUID.randomUUID(), 1));
        }

        @Override
        public boolean acknowledge(OutboxDrainService.OutboxLease lease, Instant now) {
            acknowledged = true;
            publishReturnedBeforeAcknowledge = publishReturned;
            return acknowledgeResult;
        }

        @Override
        public OutboxDrainService.FailureResult fail(
                OutboxDrainService.OutboxLease lease, String error, Instant now, Duration backoff) {
            failed = true;
            lastError = error;
            return failureResult;
        }

        private static OutboxEvent event() {
            return new OutboxEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    0,
                    "FARM",
                    UUID.randomUUID(),
                    1,
                    "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                    1,
                    NOW,
                    "{}",
                    OutboxStatus.LEASED,
                    1,
                    5,
                    NOW,
                    Optional.of(NOW.plusSeconds(30)),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of("realtime-worker-1"),
                    Optional.of(UUID.randomUUID()),
                    1,
                    Optional.empty());
        }
    }
}
