package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StockBalanceRecord(
        UUID id,
        UUID warehouseId,
        String warehouseCode,
        UUID materialId,
        String materialCode,
        String materialName,
        CanonicalUnit unit,
        BigDecimal quantityOnHand,
        BigDecimal inventoryValueVnd,
        Optional<BigDecimal> minimumStockQuantity,
        boolean lowStock,
        long version) {

    public StockBalanceRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(warehouseId, "warehouseId is required");
        Objects.requireNonNull(warehouseCode, "warehouseCode is required");
        Objects.requireNonNull(materialId, "materialId is required");
        Objects.requireNonNull(materialCode, "materialCode is required");
        Objects.requireNonNull(materialName, "materialName is required");
        Objects.requireNonNull(unit, "unit is required");
        quantityOnHand = InventoryNumbers.nonnegativeQuantity(quantityOnHand);
        inventoryValueVnd = InventoryNumbers.signedMoney(inventoryValueVnd);
        minimumStockQuantity = Objects.requireNonNull(
                minimumStockQuantity, "minimumStockQuantity is required")
                .map(InventoryNumbers::nonnegativeQuantity);
        if (inventoryValueVnd.signum() < 0 || version < 0) {
            throw new IllegalArgumentException("balance value and version must not be negative");
        }
    }
}
