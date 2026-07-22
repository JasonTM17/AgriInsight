package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.CropPage;
import java.util.List;
import java.util.Objects;

public record CropPageResponse(
        List<CropResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public CropPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static CropPageResponse from(CropPage page) {
        Objects.requireNonNull(page, "page is required");
        return new CropPageResponse(
                page.items().stream().map(CropResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
