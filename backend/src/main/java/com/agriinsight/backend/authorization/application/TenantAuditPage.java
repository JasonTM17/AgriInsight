package com.agriinsight.backend.authorization.application;

import java.util.List;
import java.util.Objects;

public record TenantAuditPage(
        List<TenantAuditRecord> items,
        int limit,
        int offset,
        boolean hasMore) {

    public TenantAuditPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || limit > 100 || offset < 0 || offset > 10_000 || items.size() > limit) {
            throw new IllegalArgumentException("page bounds are invalid");
        }
    }
}
