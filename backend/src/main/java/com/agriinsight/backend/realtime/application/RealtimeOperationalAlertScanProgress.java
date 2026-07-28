package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;

/** Durable position plus the time at which its full condition cycle began. */
public record RealtimeOperationalAlertScanProgress(
        RealtimeOperationalAlertScanCursor cursor, Instant cycleStartedAt) {

    public RealtimeOperationalAlertScanProgress {
        cursor = Objects.requireNonNull(cursor, "cursor is required");
        cycleStartedAt = Objects.requireNonNull(cycleStartedAt, "cycleStartedAt is required");
    }
}
