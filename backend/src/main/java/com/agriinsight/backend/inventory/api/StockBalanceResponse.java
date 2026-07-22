package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.StockBalanceRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record StockBalanceResponse(
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

    static StockBalanceResponse from(StockBalanceRecord balance) {
        return new StockBalanceResponse(
                balance.id(), balance.warehouseId(), balance.warehouseCode(),
                balance.materialId(), balance.materialCode(), balance.materialName(),
                balance.unit(), balance.quantityOnHand(), balance.inventoryValueVnd(),
                balance.minimumStockQuantity(), balance.lowStock(), balance.version());
    }
}
