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

public record InventoryReversalRequest(
        @Schema(description = "Positive quantity to reverse in the material base unit",
                example = "25.0000")
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 16, fraction = 4) BigDecimal quantityBase,
        @Schema(description = "Human-readable reason for the correction",
                example = "Correct duplicate warehouse posting")
        @NotBlank @Size(max = 500) String reason,
        @Schema(description = "Optional audit reason code", example = "DUPLICATE_POSTING")
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
