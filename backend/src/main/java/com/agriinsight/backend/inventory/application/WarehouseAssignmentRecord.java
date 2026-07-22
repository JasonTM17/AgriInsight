package com.agriinsight.backend.inventory.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WarehouseAssignmentRecord(
        UUID id,
        UUID tenantId,
        UUID userProfileId,
        UUID warehouseId,
        Optional<Instant> revokedAt,
        long version) {

    public WarehouseAssignmentRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        Objects.requireNonNull(warehouseId, "warehouseId is required");
        revokedAt = Objects.requireNonNull(revokedAt, "revokedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public boolean active() {
        return revokedAt.isEmpty();
    }
}
