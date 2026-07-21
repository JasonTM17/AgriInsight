package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FieldPage;
import java.util.List;
import java.util.Objects;

public record FieldPageResponse(
        List<FieldResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public FieldPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static FieldPageResponse from(FieldPage page) {
        Objects.requireNonNull(page, "page is required");
        return new FieldPageResponse(
                page.items().stream().map(FieldResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
