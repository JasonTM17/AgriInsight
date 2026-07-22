package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record OperatingCostQuery(
        int limit,
        int offset,
        Instant occurredFrom,
        Instant occurredTo,
        Optional<UUID> farmId,
        Optional<UUID> fieldId,
        Optional<UUID> seasonId,
        Optional<UUID> activityId,
        Optional<CostCategory> category,
        Optional<CostTarget.Type> targetType,
        Optional<CostEntryKind> entryKind) {

    public OperatingCostQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
        CostPeriod.requireBounded(occurredFrom, occurredTo);
        farmId = Objects.requireNonNull(farmId, "farmId is required");
        fieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        seasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        activityId = Objects.requireNonNull(activityId, "activityId is required");
        category = Objects.requireNonNull(category, "category is required");
        targetType = Objects.requireNonNull(targetType, "targetType is required");
        entryKind = Objects.requireNonNull(entryKind, "entryKind is required");
    }
}
