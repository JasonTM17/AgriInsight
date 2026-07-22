package com.agriinsight.backend.inventory.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StockLotQuery(
        int limit,
        int offset,
        Optional<UUID> warehouseId,
        Optional<UUID> materialId,
        Optional<LocalDate> expiringBefore,
        boolean includeDepleted) {

    public StockLotQuery {
        InventoryTransactionQuery.requirePage(limit, offset);
        warehouseId = Objects.requireNonNull(warehouseId, "warehouseId is required");
        materialId = Objects.requireNonNull(materialId, "materialId is required");
        expiringBefore = Objects.requireNonNull(expiringBefore, "expiringBefore is required");
    }
}
