package com.agriinsight.backend.identity.application;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AgriInsightPrincipal(
        UUID profileId,
        UUID tenantId,
        Optional<String> displayName,
        Optional<String> email,
        Optional<String> assurance) implements Principal {

    public AgriInsightPrincipal {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        displayName = Objects.requireNonNull(displayName, "displayName is required");
        email = Objects.requireNonNull(email, "email is required");
        assurance = Objects.requireNonNull(assurance, "assurance is required");
    }

    @Override
    public String getName() {
        return profileId.toString();
    }
}
