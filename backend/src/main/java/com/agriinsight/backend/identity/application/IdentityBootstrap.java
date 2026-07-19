package com.agriinsight.backend.identity.application;

import java.util.Objects;
import java.util.UUID;

public record IdentityBootstrap(
        UUID profileId,
        UUID tenantId,
        boolean profileActive,
        boolean tenantActive) {

    public IdentityBootstrap {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
    }
}
