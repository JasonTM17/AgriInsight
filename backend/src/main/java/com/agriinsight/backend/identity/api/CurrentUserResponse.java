package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import java.util.Objects;
import java.util.UUID;

public record CurrentUserResponse(
        UUID profileId,
        UUID tenantId,
        String displayName,
        String email,
        String assurance) {

    public CurrentUserResponse {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
    }

    static CurrentUserResponse from(AgriInsightPrincipal principal) {
        return new CurrentUserResponse(
                principal.profileId(),
                principal.tenantId(),
                principal.displayName().orElse(null),
                principal.email().orElse(null),
                principal.assurance().orElse(null));
    }
}
