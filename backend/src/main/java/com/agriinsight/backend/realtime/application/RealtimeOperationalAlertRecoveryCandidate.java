package com.agriinsight.backend.realtime.application;

import java.util.Objects;
import java.util.Optional;

/** One stale open alert paired with a current, metadata-only condition check. */
public record RealtimeOperationalAlertRecoveryCandidate(
        RealtimeOpenOperationalAlert alert,
        Optional<RealtimeOperationalAlertCondition> currentCondition) {

    public RealtimeOperationalAlertRecoveryCandidate {
        alert = Objects.requireNonNull(alert, "alert is required");
        currentCondition = Objects.requireNonNull(currentCondition, "currentCondition is required");
    }
}
