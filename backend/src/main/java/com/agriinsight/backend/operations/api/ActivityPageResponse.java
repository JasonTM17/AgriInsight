package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityPage;
import java.util.List;
import java.util.Objects;

public record ActivityPageResponse(
        List<ActivityResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public ActivityPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static ActivityPageResponse from(ActivityPage page) {
        Objects.requireNonNull(page, "page is required");
        return new ActivityPageResponse(
                page.items().stream().map(ActivityResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
