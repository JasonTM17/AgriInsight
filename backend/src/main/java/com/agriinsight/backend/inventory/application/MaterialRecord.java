package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MaterialRecord(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        CanonicalUnit baseUnit,
        Optional<BigDecimal> minimumStockQuantity,
        boolean active,
        long version) {

    public MaterialRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = Material.canonicalCode(code);
        displayName = Material.canonicalDisplayName(displayName);
        Objects.requireNonNull(baseUnit, "baseUnit is required");
        minimumStockQuantity = Material.optionalMinimumStock(minimumStockQuantity);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
