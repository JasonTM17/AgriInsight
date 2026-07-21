package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.SeasonRecord;
import com.agriinsight.backend.farm.domain.Season;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SeasonResponse(
        UUID id,
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

    public SeasonResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(cropId, "cropId is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(varietyName, "varietyName is required");
        Objects.requireNonNull(startedOn, "startedOn is required");
        Objects.requireNonNull(endedOn, "endedOn is required");
        Objects.requireNonNull(budgetVnd, "budgetVnd is required");
        Objects.requireNonNull(status, "status is required");
    }

    public static SeasonResponse from(SeasonRecord season) {
        Objects.requireNonNull(season, "season is required");
        return new SeasonResponse(
                season.id(), season.farmId(), season.fieldId(), season.cropId(),
                season.code(), season.displayName(), season.varietyName(),
                season.plannedStartDate(), season.plannedEndDate(),
                season.startedOn(), season.endedOn(), season.plantedAreaHectares(),
                season.budgetVnd(), season.status(), season.version());
    }
}
