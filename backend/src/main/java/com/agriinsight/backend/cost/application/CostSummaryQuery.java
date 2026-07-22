package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.cost.domain.CostCategory;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CostSummaryQuery(
        Instant occurredFrom,
        Instant occurredTo,
        CostSummaryGroup groupBy,
        Optional<UUID> farmId,
        Optional<UUID> seasonId,
        Optional<CostCategory> category) {

    public CostSummaryQuery {
        CostPeriod.requireBounded(occurredFrom, occurredTo);
        Objects.requireNonNull(groupBy, "groupBy is required");
        farmId = Objects.requireNonNull(farmId, "farmId is required");
        seasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        category = Objects.requireNonNull(category, "category is required");
    }
}
