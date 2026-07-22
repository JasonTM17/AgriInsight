package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.StockBalancePage;
import java.util.List;

public record StockBalancePageResponse(
        List<StockBalanceResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    static StockBalancePageResponse from(StockBalancePage page) {
        return new StockBalancePageResponse(
                page.items().stream().map(StockBalanceResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
