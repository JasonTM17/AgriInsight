package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InventoryTransactionCommands {

    private InventoryTransactionCommands() {
    }

    public sealed interface Posting permits Receipt, Issue {
        UUID warehouseId();
        UUID materialId();
        BigDecimal quantityBase();
        Instant occurredAt();
        Optional<String> referenceCode();
        TenantAuditMetadata audit();
        InventoryTransactionKind kind();
    }

    public record Receipt(
            UUID warehouseId,
            UUID materialId,
            UUID supplierId,
            BigDecimal quantityBase,
            BigDecimal unitCostVnd,
            String batchCode,
            LocalDate expiryDate,
            Instant occurredAt,
            Optional<String> referenceCode,
            TenantAuditMetadata audit) implements Posting {

        public Receipt {
            requireIds(warehouseId, materialId);
            Objects.requireNonNull(supplierId, "supplierId is required");
            quantityBase = InventoryNumbers.positiveQuantity(quantityBase);
            unitCostVnd = InventoryNumbers.nonnegativeUnitCost(unitCostVnd);
            batchCode = requiredText(batchCode, "batchCode", 64);
            Objects.requireNonNull(expiryDate, "expiryDate is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            if (expiryDate.isBefore(occurredAt.atZone(ZoneOffset.UTC).toLocalDate())) {
                throw new IllegalArgumentException("expiryDate cannot precede receipt date");
            }
            referenceCode = optionalText(referenceCode, "referenceCode", 128);
            Objects.requireNonNull(audit, "audit is required");
        }

        @Override
        public InventoryTransactionKind kind() {
            return InventoryTransactionKind.RECEIPT;
        }

        public BigDecimal procurementEffectVnd() {
            return InventoryNumbers.money(quantityBase, unitCostVnd);
        }
    }

    public record Issue(
            UUID warehouseId,
            UUID materialId,
            BigDecimal quantityBase,
            Optional<UUID> stockLotId,
            Instant occurredAt,
            String reason,
            Optional<String> referenceCode,
            TenantAuditMetadata audit) implements Posting {

        public Issue {
            requireIds(warehouseId, materialId);
            quantityBase = InventoryNumbers.positiveQuantity(quantityBase);
            stockLotId = Objects.requireNonNull(stockLotId, "stockLotId is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            reason = requiredText(reason, "reason", 500);
            referenceCode = optionalText(referenceCode, "referenceCode", 128);
            Objects.requireNonNull(audit, "audit is required");
        }

        @Override
        public InventoryTransactionKind kind() {
            return InventoryTransactionKind.ISSUE;
        }
    }

    public record Reversal(
            BigDecimal quantityBase,
            String reason,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Reversal {
            quantityBase = InventoryNumbers.positiveQuantity(quantityBase);
            reason = requiredText(reason, "reason", 500);
            if (expectedVersion < 0) {
                throw new IllegalArgumentException("expectedVersion must not be negative");
            }
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    private static void requireIds(UUID warehouseId, UUID materialId) {
        Objects.requireNonNull(warehouseId, "warehouseId is required");
        Objects.requireNonNull(materialId, "materialId is required");
    }

    private static Optional<String> optionalText(
            Optional<String> value,
            String fieldName,
            int maxLength) {
        return Objects.requireNonNull(value, fieldName + " is required")
                .map(text -> requiredText(text, fieldName, maxLength));
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " is required").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
