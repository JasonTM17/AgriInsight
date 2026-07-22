package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.InventoryTransactionPage;
import java.util.List;

public record InventoryTransactionPageResponse(
        List<InventoryTransactionResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    static InventoryTransactionPageResponse from(InventoryTransactionPage page) {
        return new InventoryTransactionPageResponse(
                page.items().stream().map(InventoryTransactionResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
