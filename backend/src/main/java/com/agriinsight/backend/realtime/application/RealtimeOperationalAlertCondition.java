package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A validated, payload-free condition observed by an operational alert policy. */
public record RealtimeOperationalAlertCondition(
        RealtimeOperationalAlertPolicy policy,
        UUID tenantId,
        UUID sourceEventId,
        Instant sourceOccurredAt) {

    public RealtimeOperationalAlertCondition {
        policy = Objects.requireNonNull(policy, "policy is required");
        tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        sourceOccurredAt = Objects.requireNonNull(sourceOccurredAt, "sourceOccurredAt is required");
        policy.dedupeKey(tenantId, sourceEventId);
    }

    public String dedupeKey() {
        return policy.dedupeKey(tenantId, sourceEventId);
    }
}
