package com.agriinsight.backend.inventory.application;

import java.util.List;
import java.util.Objects;

public record WarehouseAssignmentPage(
        List<WarehouseAssignmentRecord> items,
        int limit,
        int offset,
        boolean hasMore) {

    public WarehouseAssignmentPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || limit > 100 || offset < 0 || offset > 10_000 || items.size() > limit) {
            throw new IllegalArgumentException("page bounds are invalid");
        }
    }
}
