package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityAssignmentPage;
import java.util.List;
import java.util.Objects;

public record ActivityAssignmentPageResponse(
        List<ActivityAssignmentResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public ActivityAssignmentPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static ActivityAssignmentPageResponse from(ActivityAssignmentPage page) {
        Objects.requireNonNull(page, "page is required");
        return new ActivityAssignmentPageResponse(
                page.items().stream().map(ActivityAssignmentResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
