package com.agriinsight.backend.operations.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record HarvestQuery(
        int limit, int offset,
        Optional<UUID> farmId, Optional<UUID> fieldId,
        Optional<UUID> seasonId, Optional<UUID> cropId,
        Optional<LocalDate> occurredFrom, Optional<LocalDate> occurredTo) {

    public HarvestQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
        farmId = Objects.requireNonNull(farmId, "farmId is required");
        fieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        seasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        cropId = Objects.requireNonNull(cropId, "cropId is required");
        occurredFrom = Objects.requireNonNull(occurredFrom, "occurredFrom is required");
        occurredTo = Objects.requireNonNull(occurredTo, "occurredTo is required");
        if (occurredFrom.isPresent() && occurredTo.isPresent()
                && occurredTo.orElseThrow().isBefore(occurredFrom.orElseThrow())) {
            throw new IllegalArgumentException("occurredTo must not be before occurredFrom");
        }
    }
}
