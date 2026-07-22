package com.agriinsight.backend.integration.application;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OutboxDrainService {

    private static final int MAX_BATCH_SIZE = 100;
    private static final Duration MAX_LEASE = Duration.ofMinutes(15);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);
    private final OutboxStore store;

    public OutboxDrainService(OutboxStore store) {
        this.store = Objects.requireNonNull(store, "store is required");
    }

    public List<OutboxLease> lease(String owner, int limit, Duration leaseDuration, Instant now) {
        String requiredOwner = requireOwner(owner);
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_BATCH_SIZE);
        }
        Duration requiredLease = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (requiredLease.isNegative() || requiredLease.isZero() || requiredLease.compareTo(MAX_LEASE) > 0) {
            throw new IllegalArgumentException("leaseDuration must be between 1 second and 15 minutes");
        }
        return store.lease(requiredOwner, limit, requiredLease, Objects.requireNonNull(now, "now is required"));
    }

    public boolean acknowledge(OutboxLease lease, Instant now) {
        OutboxLease required = Objects.requireNonNull(lease, "lease is required");
        return store.acknowledge(required, Objects.requireNonNull(now, "now is required"));
    }

    public FailureResult fail(OutboxLease lease, String error, Instant now) {
        OutboxLease required = Objects.requireNonNull(lease, "lease is required");
        String normalized = Objects.requireNonNull(error, "error is required").strip();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new IllegalArgumentException("error must contain 1 to 1000 characters");
        }
        Instant requiredNow = Objects.requireNonNull(now, "now is required");
        int exponent = Math.min(Math.max(required.event().attempts() - 1, 0), 9);
        Duration backoff = Duration.ofSeconds(Math.min(1L << exponent, MAX_BACKOFF.toSeconds()));
        return store.fail(required, normalized, requiredNow, backoff);
    }

    private String requireOwner(String owner) {
        String required = Objects.requireNonNull(owner, "owner is required").strip();
        if (!required.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("owner has an invalid format");
        }
        return required;
    }

    public record OutboxLease(OutboxEvent event, String owner, UUID token, long generation) {

        public OutboxLease {
            Objects.requireNonNull(event, "event is required");
            Objects.requireNonNull(owner, "owner is required");
            Objects.requireNonNull(token, "token is required");
            if (generation < 1) {
                throw new IllegalArgumentException("generation must be positive");
            }
        }
    }

    public enum FailureResult {
        REQUEUED,
        DEAD_LETTER,
        STALE
    }
}
