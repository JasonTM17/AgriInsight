package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.MaterialRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MaterialResponse(
        UUID id,
        String code,
        String displayName,
        CanonicalUnit baseUnit,
        Optional<BigDecimal> minimumStockQuantity,
        boolean active,
        long version) {

    public MaterialResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(baseUnit, "baseUnit is required");
        minimumStockQuantity = Objects.requireNonNull(
                minimumStockQuantity, "minimumStockQuantity is required");
    }

    public static MaterialResponse from(MaterialRecord material) {
        Objects.requireNonNull(material, "material is required");
        return new MaterialResponse(
                material.id(),
                material.code(),
                material.displayName(),
                material.baseUnit(),
                material.minimumStockQuantity(),
                material.active(),
                material.version());
    }
}
