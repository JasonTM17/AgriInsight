package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FarmPage;
import java.util.List;
import java.util.Objects;

public record FarmPageResponse(
        List<FarmResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public FarmPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static FarmPageResponse from(FarmPage page) {
        Objects.requireNonNull(page, "page is required");
        return new FarmPageResponse(
                page.items().stream().map(FarmResponse::from).toList(),
                page.limit(),
                page.offset(),
                page.hasMore());
    }
}
