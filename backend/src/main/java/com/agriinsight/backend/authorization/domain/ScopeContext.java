package com.agriinsight.backend.authorization.domain;

import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ScopeContext(
        UUID tenantId,
        UUID profileId,
        Type type,
        Optional<UUID> resourceId) {

    public ScopeContext {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(type, "type is required");
        resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        if (type == Type.TENANT && resourceId.isPresent()) {
            throw new IllegalArgumentException("Tenant scope cannot target a domain resource");
        }
    }

    public static ScopeContext tenant(TenantPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        return new ScopeContext(
                principal.tenantId(),
                principal.profileId(),
                Type.TENANT,
                Optional.empty());
    }

    public static ScopeContext domain(
            TenantPrincipal principal,
            Type type,
            Optional<UUID> resourceId) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(type, "type is required");
        if (type == Type.TENANT) {
            throw new IllegalArgumentException("Use tenant scope for tenant-wide access");
        }
        return new ScopeContext(
                principal.tenantId(),
                principal.profileId(),
                type,
                resourceId);
    }

    public enum Type {
        TENANT,
        FARM,
        WAREHOUSE,
        ACTIVITY
    }
}
