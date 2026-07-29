package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Fixed latest-open-alert window returned by the v1 application boundary. */
public record RealtimeOperationalAlertFeed(
        Instant generatedAt,
        List<RealtimeOperationalAlertView> items,
        int limit,
        boolean hasMore) {

    public static final int LIMIT = 50;

    public RealtimeOperationalAlertFeed {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt is required");
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit != LIMIT || items.size() > limit) {
            throw new IllegalArgumentException("Operational alert feed must use the fixed limit");
        }
    }
}
