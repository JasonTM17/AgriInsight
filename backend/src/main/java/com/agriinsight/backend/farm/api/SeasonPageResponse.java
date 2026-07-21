package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.SeasonPage;
import java.util.List;
import java.util.Objects;

public record SeasonPageResponse(
        List<SeasonResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public SeasonPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static SeasonPageResponse from(SeasonPage page) {
        Objects.requireNonNull(page, "page is required");
        return new SeasonPageResponse(
                page.items().stream().map(SeasonResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
