package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InventoryTransactionRecord(
        UUID id,
        UUID tenantId,
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

    public InventoryTransactionRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(warehouseId, "warehouseId is required");
        Objects.requireNonNull(materialId, "materialId is required");
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(unit, "unit is required");
        quantityBase = InventoryNumbers.positiveQuantity(quantityBase);
        signedQuantityEffect = signedQuantity(signedQuantityEffect, quantityBase);
        unitCostVnd = Objects.requireNonNull(unitCostVnd, "unitCostVnd is required")
                .map(InventoryNumbers::nonnegativeUnitCost);
        procurementEffectVnd = InventoryNumbers.signedMoney(procurementEffectVnd);
        supplierId = Objects.requireNonNull(supplierId, "supplierId is required");
        batchCode = Objects.requireNonNull(batchCode, "batchCode is required");
        expiryDate = Objects.requireNonNull(expiryDate, "expiryDate is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        reason = Objects.requireNonNull(reason, "reason is required");
        referenceCode = Objects.requireNonNull(referenceCode, "referenceCode is required");
        reversalOf = Objects.requireNonNull(reversalOf, "reversalOf is required");
        Objects.requireNonNull(recordedByProfileId, "recordedByProfileId is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        requireShape(kind, signedQuantityEffect, unitCostVnd, procurementEffectVnd,
                supplierId, batchCode, expiryDate, reason, reversalOf);
    }

    private static BigDecimal signedQuantity(BigDecimal value, BigDecimal quantity) {
        BigDecimal required = Objects.requireNonNull(value, "signedQuantityEffect is required");
        if (required.signum() == 0
                || InventoryNumbers.positiveQuantity(required.abs()).compareTo(quantity) != 0) {
            throw new IllegalArgumentException(
                    "signedQuantityEffect must equal quantityBase in magnitude");
        }
        return required.signum() < 0 ? quantity.negate() : quantity;
    }

    private static void requireShape(
            InventoryTransactionKind kind,
            BigDecimal signedQuantity,
            Optional<BigDecimal> unitCost,
            BigDecimal procurementEffect,
            Optional<UUID> supplierId,
            Optional<String> batchCode,
            Optional<LocalDate> expiryDate,
            Optional<String> reason,
            Optional<UUID> reversalOf) {
        boolean valid = switch (kind) {
            case RECEIPT -> signedQuantity.signum() > 0
                    && unitCost.isPresent() && procurementEffect.signum() >= 0
                    && supplierId.isPresent() && batchCode.isPresent() && expiryDate.isPresent()
                    && reversalOf.isEmpty();
            case ISSUE -> signedQuantity.signum() < 0
                    && unitCost.isEmpty() && procurementEffect.signum() == 0
                    && supplierId.isEmpty() && batchCode.isEmpty() && expiryDate.isEmpty()
                    && reason.isPresent() && reversalOf.isEmpty();
            case REVERSAL -> reason.isPresent() && reversalOf.isPresent();
        };
        if (!valid) {
            throw new IllegalArgumentException("inventory transaction shape is invalid");
        }
    }
}
