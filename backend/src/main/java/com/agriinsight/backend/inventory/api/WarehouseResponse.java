package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.WarehouseRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String displayName,
        Optional<String> locationText,
        boolean active,
        long version) {

    public WarehouseResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
        locationText = Objects.requireNonNull(locationText, "locationText is required");
    }

    public static WarehouseResponse from(WarehouseRecord warehouse) {
        Objects.requireNonNull(warehouse, "warehouse is required");
        return new WarehouseResponse(
                warehouse.id(),
                warehouse.code(),
                warehouse.displayName(),
                warehouse.locationText(),
                warehouse.active(),
                warehouse.version());
    }
}
