package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.ExternalIdentityReference;
import java.util.Objects;
import java.util.UUID;

public record ExternalIdentityResponse(
        UUID id,
        String issuer,
        boolean active,
        long version) {

    public ExternalIdentityResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(issuer, "issuer is required");
    }

    public static ExternalIdentityResponse from(ExternalIdentityReference identity) {
        Objects.requireNonNull(identity, "identity is required");
        return new ExternalIdentityResponse(
                identity.id(),
                identity.issuer(),
                identity.active(),
                identity.version());
    }
}
