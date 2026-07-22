package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.farm.domain.Season;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SeasonRecord(
        UUID id,
        UUID tenantId,
        UUID farmId,
        UUID fieldId,
        UUID cropId,
        String code,
        String displayName,
        Optional<String> varietyName,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        Optional<LocalDate> startedOn,
        Optional<LocalDate> endedOn,
        BigDecimal plantedAreaHectares,
        Optional<BigDecimal> budgetVnd,
        Season.Status status,
        long version) {

    public SeasonRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(cropId, "cropId is required");
        code = Season.canonicalCode(code);
        displayName = Season.canonicalDisplayName(displayName);
        varietyName = Season.optionalText(
                varietyName, "varietyName", Season.VARIETY_NAME_MAX_LENGTH);
        Season.requireDateRange(plannedStartDate, plannedEndDate);
        startedOn = Objects.requireNonNull(startedOn, "startedOn is required");
        endedOn = Objects.requireNonNull(endedOn, "endedOn is required");
        if (startedOn.isPresent() && endedOn.isPresent()
                && endedOn.orElseThrow().isBefore(startedOn.orElseThrow())) {
            throw new IllegalArgumentException("endedOn must not be before startedOn");
        }
        plantedAreaHectares = Season.positiveArea(plantedAreaHectares);
        budgetVnd = Season.optionalBudget(budgetVnd);
        status = Objects.requireNonNull(status, "status is required");
        requireStatusDates(status, startedOn, endedOn);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    private static void requireStatusDates(
            Season.Status status,
            Optional<LocalDate> startedOn,
            Optional<LocalDate> endedOn) {
        boolean valid = switch (status) {
            case PLANNED -> startedOn.isEmpty() && endedOn.isEmpty();
            case ACTIVE -> startedOn.isPresent() && endedOn.isEmpty();
            case COMPLETED -> startedOn.isPresent() && endedOn.isPresent();
            case CANCELLED -> endedOn.isPresent();
        };
        if (!valid) {
            throw new IllegalArgumentException("season status dates are invalid");
        }
    }
}
