package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantAuditPage;
import java.util.List;
import java.util.Objects;

public record TenantAuditPageResponse(
        List<TenantAuditResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public TenantAuditPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static TenantAuditPageResponse from(TenantAuditPage page) {
        Objects.requireNonNull(page, "page is required");
        return new TenantAuditPageResponse(
                page.items().stream().map(TenantAuditResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
