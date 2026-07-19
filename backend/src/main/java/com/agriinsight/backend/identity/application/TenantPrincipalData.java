package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.identity.domain.Permission;
import com.agriinsight.backend.identity.domain.Role;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record TenantPrincipalData(
        UUID profileId,
        UUID tenantId,
        String tenantCode,
        String displayName,
        Optional<String> email,
        Set<Role> roles,
        Set<Permission> permissions) {

    public TenantPrincipalData {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(tenantCode, "tenantCode is required");
        Objects.requireNonNull(displayName, "displayName is required");
        email = Objects.requireNonNull(email, "email is required");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles are required"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions are required"));
    }
}
