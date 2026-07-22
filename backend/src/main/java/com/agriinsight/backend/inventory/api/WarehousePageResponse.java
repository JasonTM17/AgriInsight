package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.WarehousePage;
import java.util.List;
import java.util.Objects;

public record WarehousePageResponse(
        List<WarehouseResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public WarehousePageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static WarehousePageResponse from(WarehousePage page) {
        Objects.requireNonNull(page, "page is required");
        return new WarehousePageResponse(
                page.items().stream().map(WarehouseResponse::from).toList(),
                page.limit(),
                page.offset(),
                page.hasMore());
    }
}
