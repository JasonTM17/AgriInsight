package com.agriinsight.backend.identity.application;

import java.net.URL;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

public final class PrincipalMapper {

    private final ExternalIdentityService identityService;
    private final String displayNameClaim;
    private final String emailClaim;
    private final String assuranceClaim;

    public PrincipalMapper(
            ExternalIdentityService identityService,
            String displayNameClaim,
            String emailClaim,
            String assuranceClaim) {
        this.identityService = Objects.requireNonNull(identityService, "identityService is required");
        this.displayNameClaim = Objects.requireNonNull(displayNameClaim, "displayNameClaim is required");
        this.emailClaim = Objects.requireNonNull(emailClaim, "emailClaim is required");
        this.assuranceClaim = Objects.requireNonNull(assuranceClaim, "assuranceClaim is required");
    }

    public AgriInsightPrincipal map(Jwt jwt) {
        URL issuer = Objects.requireNonNull(jwt.getIssuer(), "Verified issuer is required");
        return identityService.resolve(new ExternalIdentityClaims(
                issuer.toExternalForm(),
                jwt.getSubject(),
                optionalStringClaim(jwt, displayNameClaim),
                optionalStringClaim(jwt, emailClaim),
                optionalStringClaim(jwt, assuranceClaim)));
    }

    private String optionalStringClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Configured identity display claim must be a string");
    }
}
