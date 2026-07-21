package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Season;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public record SeasonUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 200) String displayName,
        @Size(max = 200) String varietyName,
        Boolean clearVarietyName,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        @Positive @Digits(integer = 10, fraction = 4) BigDecimal plantedAreaHectares,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal budgetVnd,
        Boolean clearBudgetVnd,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public SeasonUpdateRequest {
        code = code == null ? null : Season.canonicalCode(code);
        displayName = displayName == null ? null : Season.canonicalDisplayName(displayName);
        varietyName = normalizeOptionalText(varietyName);
        plantedAreaHectares = plantedAreaHectares == null
                ? null : Season.positiveArea(plantedAreaHectares);
        budgetVnd = budgetVnd == null
                ? null : Season.optionalBudget(Optional.of(budgetVnd)).orElseThrow();
        reasonCode = normalizeReason(reasonCode);
        clearVarietyName = Boolean.TRUE.equals(clearVarietyName);
        clearBudgetVnd = Boolean.TRUE.equals(clearBudgetVnd);
        if (clearVarietyName && varietyName != null) {
            throw new IllegalArgumentException("varietyName cannot be set and cleared together");
        }
        if (clearBudgetVnd && budgetVnd != null) {
            throw new IllegalArgumentException("budgetVnd cannot be set and cleared together");
        }
        if (code == null && displayName == null && varietyName == null && !clearVarietyName
                && plannedStartDate == null && plannedEndDate == null
                && plantedAreaHectares == null && budgetVnd == null && !clearBudgetVnd) {
            throw new IllegalArgumentException("at least one season field must be provided");
        }
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? null
                : Season.optionalText(Optional.of(value), "varietyName", 200).orElseThrow();
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
