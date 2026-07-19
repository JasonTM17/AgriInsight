package com.agriinsight.backend.identity.application;

import java.util.Objects;

public record ProvisionedTenantUser(
        TenantUserProfile profile,
        ExternalIdentityReference identity) {

    public ProvisionedTenantUser {
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(identity, "identity is required");
    }
}
