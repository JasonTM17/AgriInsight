package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.cost.application.CostCommands;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record OperatingCostCorrectionRequest(
        @NotNull CostTarget.Type targetType,
        UUID targetId,
        @NotNull CostCategory category,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 17, fraction = 2) BigDecimal amountVnd,
        @NotNull Instant occurredAt,
        @Size(max = OperatingCostEntry.DESCRIPTION_MAX_LENGTH) String description,
        @Size(max = OperatingCostEntry.SOURCE_REFERENCE_MAX_LENGTH) String sourceReference,
        @Schema(description = "Why reversal and replacement are required",
                example = "Correct invoice allocation")
        @NotBlank @Size(max = CostCommands.CORRECTION_REASON_MAX_LENGTH)
        String correctionReason,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public OperatingCostCorrectionRequest {
        description = CostApiRequestSupport.optionalText(description);
        sourceReference = CostApiRequestSupport.optionalText(sourceReference);
        correctionReason = CostApiRequestSupport.optionalText(correctionReason);
        reasonCode = CostApiRequestSupport.reasonCode(reasonCode);
    }

    CostCommands.Correct toCommand(TenantAuditMetadata audit) {
        return new CostCommands.Correct(
                CostApiRequestSupport.target(targetType, targetId),
                category,
                amountVnd,
                occurredAt,
                Optional.ofNullable(description),
                Optional.ofNullable(sourceReference),
                correctionReason,
                audit);
    }
}
