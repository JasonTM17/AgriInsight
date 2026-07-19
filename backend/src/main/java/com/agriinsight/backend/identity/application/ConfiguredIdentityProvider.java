package com.agriinsight.backend.identity.application;

import java.util.Objects;

@FunctionalInterface
public interface ConfiguredIdentityProvider {

    String issuer();

    default void requireConfiguredIssuer(String candidate) {
        Objects.requireNonNull(candidate, "issuer is required");
        String configuredIssuer = Objects.requireNonNull(issuer(), "configured issuer is required");
        if (!configuredIssuer.equals(candidate)) {
            throw new IllegalArgumentException("issuer must match the configured identity provider");
        }
    }
}
