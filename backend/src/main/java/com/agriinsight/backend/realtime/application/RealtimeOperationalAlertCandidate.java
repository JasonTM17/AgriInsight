package com.agriinsight.backend.realtime.application;

import java.util.Objects;

/** One bounded source condition paired with its stable worker-only scan position. */
public record RealtimeOperationalAlertCandidate(
        RealtimeOperationalAlertCondition condition, RealtimeOperationalAlertScanCursor scanCursor) {

    public RealtimeOperationalAlertCandidate {
        condition = Objects.requireNonNull(condition, "condition is required");
        scanCursor = Objects.requireNonNull(scanCursor, "scanCursor is required");
    }
}
