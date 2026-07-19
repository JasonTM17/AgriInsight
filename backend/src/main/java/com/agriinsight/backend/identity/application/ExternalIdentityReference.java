package com.agriinsight.backend.identity.application;

import java.util.Objects;
import java.util.UUID;

public record ExternalIdentityReference(
        UUID id,
        String issuer,
        boolean active,
        long version) {

    public ExternalIdentityReference {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(issuer, "issuer is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
