package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        UUID warehouseId,
        UUID materialId,
        InventoryTransactionKind kind,
        CanonicalUnit unit,
        BigDecimal quantityBase,
        BigDecimal signedQuantityEffect,
        Optional<BigDecimal> unitCostVnd,
        BigDecimal procurementEffectVnd,
        Optional<UUID> supplierId,
        Optional<String> batchCode,
        Optional<LocalDate> expiryDate,
        Instant occurredAt,
        Optional<String> reason,
        Optional<String> referenceCode,
        Optional<UUID> reversalOf,
        UUID recordedByProfileId,
        long version) {

    public static InventoryTransactionResponse from(InventoryTransactionRecord transaction) {
        return new InventoryTransactionResponse(
                transaction.id(), transaction.warehouseId(), transaction.materialId(),
                transaction.kind(), transaction.unit(), transaction.quantityBase(),
                transaction.signedQuantityEffect(), transaction.unitCostVnd(),
                transaction.procurementEffectVnd(), transaction.supplierId(),
                transaction.batchCode(), transaction.expiryDate(), transaction.occurredAt(),
                transaction.reason(), transaction.referenceCode(), transaction.reversalOf(),
                transaction.recordedByProfileId(), transaction.version());
    }
}
