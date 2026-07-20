package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.TenantUserProfile;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TenantUserResponse(
        UUID id,
        String displayName,
        Optional<String> email,
        boolean active,
        long version) {

    public TenantUserResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(displayName, "displayName is required");
        email = Objects.requireNonNull(email, "email is required");
    }

    public static TenantUserResponse from(TenantUserProfile profile) {
        Objects.requireNonNull(profile, "profile is required");
        return new TenantUserResponse(
                profile.id(),
                profile.displayName(),
                profile.email(),
                profile.active(),
                profile.version());
    }
}
