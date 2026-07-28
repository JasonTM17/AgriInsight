package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Tenant-wide realtime operational summary; source Kafka values are intentionally excluded. */
public record RealtimeSummary(
        UUID tenantId,
        long eventCount,
        Optional<Instant> lastOccurredAt,
        Optional<Instant> lastProcessedAt,
        long freshnessSeconds,
        List<RealtimeMetric> items,
        int limit,
        boolean hasMore) {

    public static final String LENS = "REALTIME_OPERATIONAL";
    public static final String SOURCE = "KAFKA_READ_MODEL";

    public RealtimeSummary {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (eventCount < 0 || freshnessSeconds < 0) {
            throw new IllegalArgumentException("Realtime summary counts must not be negative");
        }
        lastOccurredAt = Objects.requireNonNull(lastOccurredAt, "lastOccurredAt is required");
        lastProcessedAt = Objects.requireNonNull(lastProcessedAt, "lastProcessedAt is required");
        if (lastOccurredAt.isPresent() != lastProcessedAt.isPresent()) {
            throw new IllegalArgumentException("Realtime summary timestamps must be present together");
        }
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || items.size() > limit) {
            throw new IllegalArgumentException("Realtime summary page metadata is invalid");
        }
    }
}
