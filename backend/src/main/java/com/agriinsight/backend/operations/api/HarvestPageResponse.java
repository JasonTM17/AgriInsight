package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.HarvestPage;
import java.util.List;

public record HarvestPageResponse(
        List<HarvestResponse> items, int limit, int offset, boolean hasMore) {

    public static HarvestPageResponse from(HarvestPage page) {
        return new HarvestPageResponse(
                page.items().stream().map(HarvestResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
