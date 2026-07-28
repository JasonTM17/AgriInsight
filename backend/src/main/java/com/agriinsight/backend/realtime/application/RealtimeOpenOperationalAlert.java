package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Worker-owned recovery state for one currently open alert. */
public record RealtimeOpenOperationalAlert(
        UUID id, String dedupeKey, Instant cleanSince, int cleanScanCount) {

    public RealtimeOpenOperationalAlert {
        id = Objects.requireNonNull(id, "id is required");
        dedupeKey = Objects.requireNonNull(dedupeKey, "dedupeKey is required");
        if (!dedupeKey.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("dedupeKey has an invalid format");
        }
        if (cleanScanCount < 0) {
            throw new IllegalArgumentException("cleanScanCount must not be negative");
        }
    }
}
