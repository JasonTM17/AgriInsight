package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CurrentUserResponse(
        UUID profileId,
        UUID tenantId,
        String tenantCode,
        String displayName,
        String email,
        String assurance,
        List<String> roles,
        List<String> permissions) {

    public CurrentUserResponse {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(tenantCode, "tenantCode is required");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles are required"));
        permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions are required"));
    }

    static CurrentUserResponse from(AgriInsightPrincipal principal) {
        return new CurrentUserResponse(
                principal.profileId(),
                principal.tenantId(),
                principal.tenantCode(),
                principal.displayName().orElse(null),
                principal.email().orElse(null),
                principal.assurance().orElse(null),
                principal.roles().stream().map(Enum::name).sorted().toList(),
                principal.permissions().stream().map(Enum::name).sorted().toList());
    }
}
