package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;

/** Next persisted recovery state after one clean evaluation scan. */
public record RealtimeAlertRecoveryTransition(
        Instant cleanSince, int cleanScanCount, boolean resolve) {

    public RealtimeAlertRecoveryTransition {
        cleanSince = Objects.requireNonNull(cleanSince, "cleanSince is required");
        if (cleanScanCount < 1) {
            throw new IllegalArgumentException("cleanScanCount must be positive");
        }
    }
}
