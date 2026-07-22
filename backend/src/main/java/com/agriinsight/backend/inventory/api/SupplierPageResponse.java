package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.SupplierPage;
import java.util.List;
import java.util.Objects;

public record SupplierPageResponse(
        List<SupplierResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public SupplierPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static SupplierPageResponse from(SupplierPage page) {
        Objects.requireNonNull(page, "page is required");
        return new SupplierPageResponse(
                page.items().stream().map(SupplierResponse::from).toList(),
                page.limit(),
                page.offset(),
                page.hasMore());
    }
}
