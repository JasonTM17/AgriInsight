package com.agriinsight.backend.inventory.application;

import java.util.List;
import java.util.Objects;

public record InventoryTransactionPage(
        List<InventoryTransactionRecord> items,
        int limit,
        int offset,
        boolean hasMore) {

    public InventoryTransactionPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        InventoryTransactionQuery.requirePage(limit, offset);
        if (items.size() > limit) {
            throw new IllegalArgumentException("page items must not exceed limit");
        }
    }
}
