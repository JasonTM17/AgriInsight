package com.agriinsight.backend.inventory.domain;

import java.util.Objects;
import java.util.UUID;

public record WarehouseAssignment(
        UUID id,
        UUID tenantId,
        UUID userProfileId,
        UUID warehouseId) {

    public WarehouseAssignment {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        Objects.requireNonNull(warehouseId, "warehouseId is required");
    }
}
