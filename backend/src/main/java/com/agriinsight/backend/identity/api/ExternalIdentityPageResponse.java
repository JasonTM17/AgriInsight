package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.ExternalIdentityPage;
import java.util.List;
import java.util.Objects;

public record ExternalIdentityPageResponse(
        List<ExternalIdentityResponse> items,
        int limit,
        int offset,
        boolean hasMore) {

    public ExternalIdentityPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    public static ExternalIdentityPageResponse from(ExternalIdentityPage page) {
        Objects.requireNonNull(page, "page is required");
        return new ExternalIdentityPageResponse(
                page.items().stream().map(ExternalIdentityResponse::from).toList(),
                page.limit(), page.offset(), page.hasMore());
    }
}
