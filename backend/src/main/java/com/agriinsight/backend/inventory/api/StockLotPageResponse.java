package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.StockLotPage;
import java.util.List;

public record StockLotPageResponse(
        List<StockLotResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    static StockLotPageResponse from(StockLotPage page) {
        return new StockLotPageResponse(
                page.items().stream().map(StockLotResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
