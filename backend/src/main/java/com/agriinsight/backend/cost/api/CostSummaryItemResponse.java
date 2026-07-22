package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.CostSummaryItem;
import java.math.BigDecimal;
import java.util.UUID;

public record CostSummaryItemResponse(
        UUID groupId,
        String groupKey,
        BigDecimal postingAmountVnd,
        BigDecimal reversalAmountVnd,
        BigDecimal netOperatingCostVnd,
        BigDecimal seasonBudgetVnd,
        BigDecimal budgetVarianceVnd) {

    static CostSummaryItemResponse from(CostSummaryItem item) {
        return new CostSummaryItemResponse(
                item.groupId().orElse(null), item.groupKey(), item.postingAmountVnd(),
                item.reversalAmountVnd(), item.netOperatingCostVnd(),
                item.seasonBudgetVnd().orElse(null), item.budgetVarianceVnd().orElse(null));
    }
}
