package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantRoleAssignmentPage;
import java.util.List;
import java.util.Objects;

public record TenantRolePageResponse(
        List<TenantRoleResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public TenantRolePageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static TenantRolePageResponse from(TenantRoleAssignmentPage page) {
        Objects.requireNonNull(page, "page is required");
        return new TenantRolePageResponse(
                page.items().stream().map(TenantRoleResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
