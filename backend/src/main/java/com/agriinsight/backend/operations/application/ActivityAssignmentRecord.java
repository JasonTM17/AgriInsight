package com.agriinsight.backend.operations.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ActivityAssignmentRecord(
        UUID id,
        UUID tenantId,
        UUID activityId,
        UUID employeeId,
        Optional<Instant> revokedAt,
        long version) {

    public ActivityAssignmentRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(employeeId, "employeeId is required");
        revokedAt = Objects.requireNonNull(revokedAt, "revokedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public boolean active() {
        return revokedAt.isEmpty();
    }
}
