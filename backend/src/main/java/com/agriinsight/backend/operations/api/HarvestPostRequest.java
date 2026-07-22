package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.operations.domain.HarvestMassUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record HarvestPostRequest(
        @NotNull UUID farmId,
        @NotNull UUID fieldId,
        @NotNull UUID seasonId,
        @NotNull UUID cropId,
        @NotNull LocalDate occurredOn,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal wasteQuantity,
        @NotNull HarvestMassUnit unit,
        @Size(max = Harvest.QUALITY_GRADE_MAX_LENGTH) String qualityGrade,
        @DecimalMin("0") BigDecimal revenueVnd,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public HarvestPostRequest {
        qualityGrade = optionalText(qualityGrade);
        reasonCode = normalizeReason(reasonCode);
    }

    static String optionalText(String value) {
        return value == null ? null : value.strip();
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank()
                ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
