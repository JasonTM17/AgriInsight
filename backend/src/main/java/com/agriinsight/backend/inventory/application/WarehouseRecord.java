package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.Warehouse;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WarehouseRecord(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        Optional<String> locationText,
        boolean active,
        long version) {

    public WarehouseRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = Warehouse.canonicalCode(code);
        displayName = Warehouse.canonicalDisplayName(displayName);
        locationText = Objects.requireNonNull(locationText, "locationText is required")
                .map(Warehouse::canonicalLocation);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
