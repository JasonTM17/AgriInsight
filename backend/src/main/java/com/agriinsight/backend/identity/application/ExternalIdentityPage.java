package com.agriinsight.backend.identity.application;

import java.util.List;
import java.util.Objects;

public record ExternalIdentityPage(
        List<ExternalIdentityReference> items,
        int limit,
        int offset,
        boolean hasMore) {

    public ExternalIdentityPage {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || limit > 100 || offset < 0 || offset > 10_000 || items.size() > limit) {
            throw new IllegalArgumentException("page bounds are invalid");
        }
    }
}
