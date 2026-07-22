package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InventoryReversalRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 16, fraction = 4) BigDecimal quantityBase,
        @NotBlank @Size(max = 500) String reason,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public InventoryReversalRequest {
        quantityBase = quantityBase == null
                ? null : InventoryNumbers.positiveQuantity(quantityBase);
        reason = reason == null ? null : reason.strip();
        reasonCode = MaterialCreateRequest.normalizeReason(reasonCode);
    }

    InventoryTransactionCommands.Reversal toCommand(
            long expectedVersion, TenantAuditMetadata audit) {
        return new InventoryTransactionCommands.Reversal(
                quantityBase, reason, expectedVersion, audit);
    }
}
