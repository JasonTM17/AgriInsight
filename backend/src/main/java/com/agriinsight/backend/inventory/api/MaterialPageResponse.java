package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.MaterialPage;
import java.util.List;
import java.util.Objects;

public record MaterialPageResponse(
        List<MaterialResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public MaterialPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static MaterialPageResponse from(MaterialPage page) {
        Objects.requireNonNull(page, "page is required");
        return new MaterialPageResponse(
                page.items().stream().map(MaterialResponse::from).toList(),
                page.limit(),
                page.offset(),
                page.hasMore());
    }
}
