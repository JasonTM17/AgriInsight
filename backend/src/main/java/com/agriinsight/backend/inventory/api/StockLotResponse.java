package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.StockLotRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockLotResponse(
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

    static StockLotResponse from(StockLotRecord lot) {
        return new StockLotResponse(
                lot.id(), lot.warehouseId(), lot.warehouseCode(), lot.materialId(),
                lot.materialCode(), lot.materialName(), lot.supplierId(), lot.supplierCode(),
                lot.originalReceiptId(), lot.batchCode(), lot.expiryDate(), lot.receivedAt(),
                lot.unit(), lot.receivedQuantity(), lot.availableQuantity(), lot.unitCostVnd(),
                lot.expired(), lot.expiringSoon(), lot.version());
    }
}
