package com.agriinsight.backend.identity.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TenantUserProfile(
        UUID id,
        UUID tenantId,
        String displayName,
        Optional<String> email,
        boolean active,
        long version) {

    public TenantUserProfile {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(displayName, "displayName is required");
        email = Objects.requireNonNull(email, "email is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
