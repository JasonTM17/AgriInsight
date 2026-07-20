package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Role;
import java.util.Objects;
import java.util.UUID;

public record TenantRoleAssignment(
        UUID id,
        UUID tenantId,
        UUID profileId,
        Role role,
        boolean active,
        long version) {

    public TenantRoleAssignment {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(role, "role is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
