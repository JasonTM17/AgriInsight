package com.agriinsight.backend.cost.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CostSummaryItem(
        Optional<UUID> groupId,
        String groupKey,
        BigDecimal postingAmountVnd,
        BigDecimal reversalAmountVnd,
        BigDecimal netOperatingCostVnd,
        Optional<BigDecimal> seasonBudgetVnd,
        Optional<BigDecimal> budgetVarianceVnd) {

    public CostSummaryItem {
        groupId = Objects.requireNonNull(groupId, "groupId is required");
        groupKey = Objects.requireNonNull(groupKey, "groupKey is required");
        Objects.requireNonNull(postingAmountVnd, "postingAmountVnd is required");
        Objects.requireNonNull(reversalAmountVnd, "reversalAmountVnd is required");
        Objects.requireNonNull(netOperatingCostVnd, "netOperatingCostVnd is required");
        seasonBudgetVnd = Objects.requireNonNull(
                seasonBudgetVnd, "seasonBudgetVnd is required");
        budgetVarianceVnd = Objects.requireNonNull(
                budgetVarianceVnd, "budgetVarianceVnd is required");
        if (seasonBudgetVnd.isPresent() != budgetVarianceVnd.isPresent()) {
            throw new IllegalArgumentException(
                    "Season budget and variance must be present together");
        }
    }
}
