package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record StockLotRecord(
        UUID id,
        UUID warehouseId,
        String warehouseCode,
        UUID materialId,
        String materialCode,
        String materialName,
        UUID supplierId,
        String supplierCode,
        UUID originalReceiptId,
        String batchCode,
        LocalDate expiryDate,
        Instant receivedAt,
        CanonicalUnit unit,
        BigDecimal receivedQuantity,
        BigDecimal availableQuantity,
        BigDecimal unitCostVnd,
        boolean expired,
        boolean expiringSoon,
        long version) {

    public StockLotRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(warehouseId, "warehouseId is required");
        Objects.requireNonNull(warehouseCode, "warehouseCode is required");
        Objects.requireNonNull(materialId, "materialId is required");
        Objects.requireNonNull(materialCode, "materialCode is required");
        Objects.requireNonNull(materialName, "materialName is required");
        Objects.requireNonNull(supplierId, "supplierId is required");
        Objects.requireNonNull(supplierCode, "supplierCode is required");
        Objects.requireNonNull(originalReceiptId, "originalReceiptId is required");
        Objects.requireNonNull(batchCode, "batchCode is required");
        Objects.requireNonNull(expiryDate, "expiryDate is required");
        Objects.requireNonNull(receivedAt, "receivedAt is required");
        Objects.requireNonNull(unit, "unit is required");
        receivedQuantity = InventoryNumbers.positiveQuantity(receivedQuantity);
        availableQuantity = InventoryNumbers.nonnegativeQuantity(availableQuantity);
        unitCostVnd = InventoryNumbers.nonnegativeUnitCost(unitCostVnd);
        if (availableQuantity.compareTo(receivedQuantity) > 0 || version < 0) {
            throw new IllegalArgumentException("stock lot quantity or version is invalid");
        }
        if (expired && expiringSoon) {
            throw new IllegalArgumentException("expired stock cannot be expiring soon");
        }
    }
}
