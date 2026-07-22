package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import com.agriinsight.backend.operations.domain.HarvestMassUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HarvestCorrectionRequest(
        @NotNull HarvestCorrectionKind correctionKind,
        @NotNull LocalDate occurredOn,
        @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @DecimalMin("0") BigDecimal wasteQuantity,
        HarvestMassUnit unit,
        @Size(max = Harvest.QUALITY_GRADE_MAX_LENGTH) String qualityGrade,
        @DecimalMin("0") BigDecimal revenueVnd,
        @NotBlank @Size(max = Harvest.CORRECTION_REASON_MAX_LENGTH) String correctionReason,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public HarvestCorrectionRequest {
        qualityGrade = HarvestPostRequest.optionalText(qualityGrade);
        correctionReason = correctionReason == null ? null : correctionReason.strip();
        reasonCode = HarvestPostRequest.normalizeReason(reasonCode);
    }

    BigDecimal quantityKg() {
        if (correctionKind == HarvestCorrectionKind.VOID && quantity == null) {
            return BigDecimal.ZERO;
        }
        requireReplacementFields();
        return unit.toKilograms(quantity, "quantity");
    }

    BigDecimal wasteQuantityKg() {
        if (correctionKind == HarvestCorrectionKind.VOID && wasteQuantity == null) {
            return BigDecimal.ZERO;
        }
        requireReplacementFields();
        return unit.toKilograms(wasteQuantity, "wasteQuantity");
    }

    private void requireReplacementFields() {
        if (quantity == null || wasteQuantity == null || unit == null) {
            throw new IllegalArgumentException(
                    "REPLACE correction requires quantity, wasteQuantity and unit");
        }
    }
}
