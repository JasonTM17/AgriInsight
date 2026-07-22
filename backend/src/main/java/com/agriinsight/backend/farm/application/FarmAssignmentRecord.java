package com.agriinsight.backend.farm.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record FarmAssignmentRecord(
        UUID id,
        UUID tenantId,
        UUID userProfileId,
        UUID farmId,
        Optional<Instant> revokedAt,
        long version) {

    public FarmAssignmentRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        revokedAt = Objects.requireNonNull(revokedAt, "revokedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public boolean active() {
        return revokedAt.isEmpty();
    }
}
