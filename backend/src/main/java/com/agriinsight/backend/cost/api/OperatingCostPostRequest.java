package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.cost.application.CostCommands;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record OperatingCostPostRequest(
        @Schema(description = "Single canonical allocation target", example = "SEASON")
        @NotNull CostTarget.Type targetType,
        @Schema(description = "Required except when targetType is TENANT",
                example = "41000000-0000-0000-0000-000000000006")
        UUID targetId,
        @Schema(description = "Versioned operating-cost category", example = "LABOR")
        @NotNull CostCategory category,
        @Schema(description = "Positive VND amount; never procurement or inventory value",
                example = "1250000.00")
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 17, fraction = 2) BigDecimal amountVnd,
        @Schema(description = "Business-effective UTC time",
                example = "2027-09-01T02:00:00Z")
        @NotNull Instant occurredAt,
        @Size(max = OperatingCostEntry.DESCRIPTION_MAX_LENGTH) String description,
        @Size(max = OperatingCostEntry.SOURCE_REFERENCE_MAX_LENGTH) String sourceReference,
        @Schema(description = "Optional audit reason code", example = "MONTHLY_POSTING")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public OperatingCostPostRequest {
        description = CostApiRequestSupport.optionalText(description);
        sourceReference = CostApiRequestSupport.optionalText(sourceReference);
        reasonCode = CostApiRequestSupport.reasonCode(reasonCode);
    }

    CostCommands.Post toCommand(TenantAuditMetadata audit) {
        return new CostCommands.Post(
                CostApiRequestSupport.target(targetType, targetId),
                category,
                amountVnd,
                occurredAt,
                Optional.ofNullable(description),
                Optional.ofNullable(sourceReference),
                audit);
    }
}
