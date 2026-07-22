package com.agriinsight.backend.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDrainServiceTest {

    @Test
    void validatesOperatorBoundsAndUsesBoundedExponentialBackoff() {
        RecordingStore store = new RecordingStore();
        OutboxDrainService service = new OutboxDrainService(store);
        var lease = new OutboxDrainService.OutboxLease(event(3), "worker-a", UUID.randomUUID(), 1);

        assertThat(service.fail(lease, " delivery failed ", Instant.parse("2027-09-01T00:00:00Z")))
                .isEqualTo(OutboxDrainService.FailureResult.REQUEUED);
        assertThat(store.backoff).isEqualTo(Duration.ofSeconds(4));
        assertThat(store.error).isEqualTo("delivery failed");
        assertThatThrownBy(() -> service.lease("worker with spaces", 1, Duration.ofSeconds(1), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lease("worker-a", 101, Duration.ofSeconds(1), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.fail(lease, " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxEvent event(int attempts) {
        Instant occurredAt = Instant.parse("2027-09-01T00:00:00Z");
        return new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, "FARM",
                UUID.randomUUID(), 0, "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED", 1,
                occurredAt, "{}", OutboxStatus.LEASED, attempts, 5, occurredAt,
                Optional.of(occurredAt.plusSeconds(30)), Optional.empty(), Optional.empty(),
                Optional.of("worker-a"), Optional.of(UUID.randomUUID()), 1, Optional.empty());
    }

    private static final class RecordingStore implements OutboxStore {
        private Duration backoff;
        private String error;

        @Override
        public List<OutboxDrainService.OutboxLease> lease(
                String owner, int limit, Duration leaseDuration, Instant now) {
            return List.of();
        }

        @Override
        public boolean acknowledge(OutboxDrainService.OutboxLease lease, Instant now) {
            return false;
        }

        @Override
        public OutboxDrainService.FailureResult fail(
                OutboxDrainService.OutboxLease lease, String error, Instant now, Duration backoff) {
            this.error = error;
            this.backoff = backoff;
            return OutboxDrainService.FailureResult.REQUEUED;
        }
    }
}
