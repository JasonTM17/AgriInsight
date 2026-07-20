package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.TenantUserPage;
import java.util.List;
import java.util.Objects;

public record TenantUserPageResponse(
        List<TenantUserResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public TenantUserPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static TenantUserPageResponse from(TenantUserPage page) {
        Objects.requireNonNull(page, "page is required");
        return new TenantUserPageResponse(
                page.items().stream().map(TenantUserResponse::from).toList(),
                page.limit(),
                page.offset(),
                page.hasMore());
    }
}
