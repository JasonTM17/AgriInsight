package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Result of one immutable profile acknowledgement revision. */
public record RealtimeAlertAcknowledgement(
        UUID alertId, Instant acknowledgedObservationAt, boolean created) {

    public RealtimeAlertAcknowledgement {
        alertId = Objects.requireNonNull(alertId, "alertId is required");
        acknowledgedObservationAt = Objects.requireNonNull(
                acknowledgedObservationAt, "acknowledgedObservationAt is required");
    }
}
