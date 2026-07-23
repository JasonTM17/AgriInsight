package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityLogPage;
import java.util.List;
import java.util.Objects;

public record ActivityLogPageResponse(
        List<ActivityLogResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public ActivityLogPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static ActivityLogPageResponse from(ActivityLogPage page) {
        Objects.requireNonNull(page, "page is required");
        return new ActivityLogPageResponse(
                page.items().stream().map(ActivityLogResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
