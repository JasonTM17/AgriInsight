package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.identity.domain.Permission;
import com.agriinsight.backend.identity.domain.Role;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AgriInsightPrincipal(
        UUID profileId,
        UUID tenantId,
        String tenantCode,
        Optional<String> displayName,
        Optional<String> email,
        Optional<String> assurance,
        Set<Role> roles,
        Set<Permission> permissions) implements TenantPrincipal {

    public AgriInsightPrincipal {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(tenantCode, "tenantCode is required");
        displayName = Objects.requireNonNull(displayName, "displayName is required");
        email = Objects.requireNonNull(email, "email is required");
        assurance = Objects.requireNonNull(assurance, "assurance is required");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles are required"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions are required"));
    }

    static AgriInsightPrincipal from(TenantPrincipalData data, String assurance) {
        return new AgriInsightPrincipal(
                data.profileId(),
                data.tenantId(),
                data.tenantCode(),
                Optional.of(data.displayName()),
                data.email(),
                Optional.ofNullable(assurance),
                data.roles(),
                data.permissions());
    }

    @Override
    public String getName() {
        return profileId.toString();
    }
}
