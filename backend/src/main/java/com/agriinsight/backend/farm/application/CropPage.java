package com.agriinsight.backend.farm.application;

import java.util.List;
import java.util.Objects;

public record CropPage(
        List<CropRecord> items,
        int limit,
        int offset,
        boolean hasMore) {

    public CropPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || limit > 100 || offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("page bounds are invalid");
        }
        if (items.size() > limit) {
            throw new IllegalArgumentException("page items must not exceed limit");
        }
    }
}
