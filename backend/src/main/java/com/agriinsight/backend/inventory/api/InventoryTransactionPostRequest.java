package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record InventoryTransactionPostRequest(
        @Schema(description = "Inventory movement type", allowableValues = {"RECEIPT", "ISSUE"},
                example = "RECEIPT")
        @NotBlank @Pattern(regexp = "RECEIPT|ISSUE") String kind,
        @Schema(description = "Warehouse receiving or issuing stock",
                example = "5a000000-0000-0000-0000-000000000001")
        @NotNull UUID warehouseId,
        @Schema(description = "Material moved by this transaction",
                example = "5a000000-0000-0000-0000-000000000003")
        @NotNull UUID materialId,
        @Schema(description = "Required for receipts and omitted for issues",
                example = "5a000000-0000-0000-0000-000000000004")
        UUID supplierId,
        @Schema(description = "Positive quantity in the material base unit",
                example = "250.0000")
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 16, fraction = 4) BigDecimal quantityBase,
        @Schema(description = "Receipt unit cost in VND; omitted for issues",
                example = "12500.00")
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal unitCostVnd,
        @Schema(description = "Supplier batch code required for receipts", example = "FERT-2027-001")
        @Size(max = 64) String batchCode,
        @Schema(description = "Lot expiry date required for receipts", example = "2028-12-31")
        LocalDate expiryDate,
        @Schema(description = "Optional source lot for a targeted issue",
                example = "5a000000-0000-0000-0000-000000000006")
        UUID stockLotId,
        @Schema(description = "Business-effective time in UTC",
                example = "2027-01-01T08:00:00Z")
        @NotNull Instant occurredAt,
        @Schema(description = "Required business reason for issues", example = "Applied to field F-12")
        @Size(max = 500) String reason,
        @Schema(description = "Optional source document or purchase-order reference",
                example = "PO-2027-00042")
        @Size(max = 128) String referenceCode,
        @Schema(description = "Optional audit reason code", example = "PLANNED_APPLICATION")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public InventoryTransactionPostRequest {
        kind = kind == null ? null : kind.strip().toUpperCase(Locale.ROOT);
        quantityBase = quantityBase == null
                ? null : InventoryNumbers.positiveQuantity(quantityBase);
        unitCostVnd = unitCostVnd == null
                ? null : InventoryNumbers.nonnegativeUnitCost(unitCostVnd);
        batchCode = optionalText(batchCode);
        reason = optionalText(reason);
        referenceCode = optionalText(referenceCode);
        reasonCode = MaterialCreateRequest.normalizeReason(reasonCode);
        if ("RECEIPT".equals(kind)) {
            requireReceiptShape(supplierId, unitCostVnd, batchCode, expiryDate, stockLotId, reason);
        } else if ("ISSUE".equals(kind)) {
            requireIssueShape(supplierId, unitCostVnd, batchCode, expiryDate, reason);
        }
    }

    public InventoryTransactionCommands.Posting toCommand(TenantAuditMetadata audit) {
        if ("RECEIPT".equals(kind)) {
            return new InventoryTransactionCommands.Receipt(
                    warehouseId, materialId, supplierId, quantityBase, unitCostVnd,
                    batchCode, expiryDate, occurredAt, Optional.ofNullable(referenceCode), audit);
        }
        return new InventoryTransactionCommands.Issue(
                warehouseId, materialId, quantityBase, Optional.ofNullable(stockLotId),
                occurredAt, reason, Optional.ofNullable(referenceCode), audit);
    }

    private static void requireReceiptShape(
            UUID supplierId,
            BigDecimal unitCost,
            String batch,
            LocalDate expiry,
            UUID stockLotId,
            String reason) {
        if (supplierId == null || unitCost == null || batch == null || expiry == null) {
            throw new IllegalArgumentException(
                    "RECEIPT requires supplierId, unitCostVnd, batchCode and expiryDate");
        }
        if (stockLotId != null || reason != null) {
            throw new IllegalArgumentException("RECEIPT cannot include issue-only fields");
        }
    }

    private static void requireIssueShape(
            UUID supplierId,
            BigDecimal unitCost,
            String batch,
            LocalDate expiry,
            String reason) {
        if (supplierId != null || unitCost != null || batch != null || expiry != null) {
            throw new IllegalArgumentException("ISSUE cannot include receipt finance or lot metadata");
        }
        if (reason == null) {
            throw new IllegalArgumentException("ISSUE requires reason");
        }
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
