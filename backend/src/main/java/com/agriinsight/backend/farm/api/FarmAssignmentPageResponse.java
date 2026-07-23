package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FarmAssignmentPage;
import java.util.List;
import java.util.Objects;

public record FarmAssignmentPageResponse(
        List<FarmAssignmentResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public FarmAssignmentPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static FarmAssignmentPageResponse from(FarmAssignmentPage page) {
        Objects.requireNonNull(page, "page is required");
        return new FarmAssignmentPageResponse(
                page.items().stream().map(FarmAssignmentResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
