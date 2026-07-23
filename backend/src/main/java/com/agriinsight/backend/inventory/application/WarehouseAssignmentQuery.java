package com.agriinsight.backend.inventory.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WarehouseAssignmentQuery(
        int limit,
        int offset,
        Optional<UUID> userProfileId,
        Optional<UUID> warehouseId,
        Optional<Boolean> active) {

    public WarehouseAssignmentQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
        userProfileId = Objects.requireNonNull(userProfileId, "userProfileId is required");
        warehouseId = Objects.requireNonNull(warehouseId, "warehouseId is required");
        active = Objects.requireNonNull(active, "active is required");
    }
}
