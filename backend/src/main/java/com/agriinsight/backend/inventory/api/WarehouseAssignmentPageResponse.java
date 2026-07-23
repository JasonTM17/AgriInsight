package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.WarehouseAssignmentPage;
import java.util.List;
import java.util.Objects;

public record WarehouseAssignmentPageResponse(
        List<WarehouseAssignmentResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public WarehouseAssignmentPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static WarehouseAssignmentPageResponse from(WarehouseAssignmentPage page) {
        Objects.requireNonNull(page, "page is required");
        return new WarehouseAssignmentPageResponse(
                page.items().stream().map(WarehouseAssignmentResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
