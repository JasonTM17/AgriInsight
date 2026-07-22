package com.agriinsight.backend.cost.application;

import java.util.List;
import java.util.Objects;

public record OperatingCostPage(
        List<OperatingCostRecord> items,
        int limit,
        int offset,
        boolean hasMore) {

    public OperatingCostPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || offset < 0 || items.size() > limit) {
            throw new IllegalArgumentException("Operating cost page metadata is invalid");
        }
    }
}
