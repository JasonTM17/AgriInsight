package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.OperatingCostPage;
import java.util.List;

public record OperatingCostPageResponse(
        List<OperatingCostResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    static OperatingCostPageResponse from(OperatingCostPage page) {
        return new OperatingCostPageResponse(
                page.items().stream().map(OperatingCostResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
