package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Season;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record SeasonCreateRequest(
        @NotNull UUID farmId,
        @NotNull UUID fieldId,
        @NotNull UUID cropId,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 200) String varietyName,
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedEndDate,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal plantedAreaHectares,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal budgetVnd,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public SeasonCreateRequest {
        code = code == null ? null : Season.canonicalCode(code);
        displayName = displayName == null ? null : Season.canonicalDisplayName(displayName);
        varietyName = normalizeOptionalText(varietyName);
        if (plannedStartDate != null && plannedEndDate != null) {
            Season.requireDateRange(plannedStartDate, plannedEndDate);
        }
        plantedAreaHectares = plantedAreaHectares == null
                ? null : Season.positiveArea(plantedAreaHectares);
        budgetVnd = budgetVnd == null
                ? null : Season.optionalBudget(Optional.of(budgetVnd)).orElseThrow();
        reasonCode = normalizeReason(reasonCode);
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? null
                : Season.optionalText(Optional.of(value), "varietyName", 200).orElseThrow();
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
