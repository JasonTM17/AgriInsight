package com.agriinsight.backend.integration.application;

import com.agriinsight.backend.integration.application.OutboxDrainService.FailureResult;
import com.agriinsight.backend.integration.application.OutboxDrainService.OutboxLease;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class OutboxPublishingService {

    private final OutboxDrainService drainService;
    private final OperationalEventPublisher publisher;
    private final Clock clock;
    private final String owner;
    private final int batchSize;
    private final Duration leaseDuration;

    public OutboxPublishingService(
            OutboxDrainService drainService,
            OperationalEventPublisher publisher,
            Clock clock,
            String owner,
            int batchSize,
            Duration leaseDuration) {
        this.drainService = Objects.requireNonNull(drainService, "drainService is required");
        this.publisher = Objects.requireNonNull(publisher, "publisher is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.owner = Objects.requireNonNull(owner, "owner is required");
        this.batchSize = batchSize;
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
    }

    public PublishResult publishAvailable() {
        List<OutboxLease> leases =
                drainService.lease(owner, batchSize, leaseDuration, clock.instant());
        int published = 0;
        int requeued = 0;
        int deadLettered = 0;
        int stale = 0;

        for (OutboxLease lease : leases) {
            try {
                publisher.publish(lease.event());
                if (drainService.acknowledge(lease, clock.instant())) {
                    published++;
                } else {
                    stale++;
                }
            } catch (RuntimeException exception) {
                FailureResult failure = drainService.fail(
                        lease,
                        "Kafka publication failed: " + exception.getClass().getSimpleName(),
                        clock.instant());
                switch (failure) {
                    case REQUEUED -> requeued++;
                    case DEAD_LETTER -> deadLettered++;
                    case STALE -> stale++;
                }
            }
        }

        return new PublishResult(leases.size(), published, requeued, deadLettered, stale);
    }

    public record PublishResult(
            int leased, int published, int requeued, int deadLettered, int stale) {
    }
}
