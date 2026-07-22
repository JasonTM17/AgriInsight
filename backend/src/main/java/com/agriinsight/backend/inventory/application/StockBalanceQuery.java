package com.agriinsight.backend.inventory.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StockBalanceQuery(
        int limit,
        int offset,
        Optional<UUID> warehouseId,
        Optional<UUID> materialId,
        Optional<Boolean> lowStock) {

    public StockBalanceQuery {
        InventoryTransactionQuery.requirePage(limit, offset);
        warehouseId = Objects.requireNonNull(warehouseId, "warehouseId is required");
        materialId = Objects.requireNonNull(materialId, "materialId is required");
        lowStock = Objects.requireNonNull(lowStock, "lowStock is required");
    }
}
