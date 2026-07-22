package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.CostSummaryGroup;
import com.agriinsight.backend.cost.application.CostSummaryResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CostSummaryResponse(
        String lens,
        String source,
        UUID tenantId,
        Instant occurredFrom,
        Instant occurredTo,
        CostSummaryGroup groupBy,
        List<CostSummaryItemResponse> items,
        int limit,
        boolean hasMore) {

    static CostSummaryResponse from(CostSummaryResult result) {
        return new CostSummaryResponse(
                CostSummaryResult.LENS, CostSummaryResult.SOURCE, result.tenantId(),
                result.occurredFrom(), result.occurredTo(), result.groupBy(),
                result.items().stream().map(CostSummaryItemResponse::from).toList(),
                result.limit(), result.hasMore());
    }
}
