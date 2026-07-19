package com.agriinsight.backend.identity.application;

import java.util.List;
import java.util.Objects;

public record TenantUserPage(
        List<TenantUserProfile> items,
        int limit,
        int offset,
        boolean hasMore) {

    public TenantUserPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (items.size() > limit || limit < 1 || offset < 0) {
            throw new IllegalArgumentException("page bounds are invalid");
        }
    }
}
