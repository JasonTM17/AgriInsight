package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Current, payload-free alert projection for one authenticated profile. */
public record RealtimeOperationalAlertView(
        UUID id,
        RealtimeOperationalAlertPolicy policy,
        RealtimeOperationalAlertSeverity severity,
        String state,
        RealtimeOperationalAlertEvidence evidence,
        Instant openedAt,
        Instant sourceOccurredAt,
        Instant lastObservedAt,
        Instant lastEvaluatedAt,
        long ageSeconds,
        boolean acknowledged,
        Optional<Instant> acknowledgedAt,
        long version) {

    public static final String SOURCE = "realtime_operational";
    public static final String OPEN = "OPEN";

    public RealtimeOperationalAlertView {
        id = Objects.requireNonNull(id, "id is required");
        policy = Objects.requireNonNull(policy, "policy is required");
        severity = Objects.requireNonNull(severity, "severity is required");
        state = Objects.requireNonNull(state, "state is required");
        if (!OPEN.equals(state)) {
            throw new IllegalArgumentException("Only open alerts may be represented");
        }
        evidence = Objects.requireNonNull(evidence, "evidence is required");
        openedAt = Objects.requireNonNull(openedAt, "openedAt is required");
        sourceOccurredAt = Objects.requireNonNull(sourceOccurredAt, "sourceOccurredAt is required");
        lastObservedAt = Objects.requireNonNull(lastObservedAt, "lastObservedAt is required");
        lastEvaluatedAt = Objects.requireNonNull(lastEvaluatedAt, "lastEvaluatedAt is required");
        acknowledgedAt = Objects.requireNonNull(acknowledgedAt, "acknowledgedAt is required");
        if (acknowledged != acknowledgedAt.isPresent()) {
            throw new IllegalArgumentException("Acknowledgement state and time do not match");
        }
        if (ageSeconds < 0) {
            throw new IllegalArgumentException("ageSeconds must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
