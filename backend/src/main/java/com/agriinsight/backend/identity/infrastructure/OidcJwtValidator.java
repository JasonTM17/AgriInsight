package com.agriinsight.backend.identity.infrastructure;

import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

public final class OidcJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token validation failed",
            null);

    private final OidcIdentityProperties properties;
    private final OAuth2TokenValidator<Jwt> delegate;

    public OidcJwtValidator(OidcIdentityProperties properties) {
        this.properties = properties;
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(properties.clockSkew());
        timestampValidator.setAllowEmptyExpiryClaim(false);
        timestampValidator.setAllowEmptyNotBeforeClaim(true);
        this.delegate = new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuerUri()),
                this::validateAudience,
                this::validateSubject,
                this::validateDiscriminator);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return delegate.validate(token);
    }

    private OAuth2TokenValidatorResult validateAudience(Jwt token) {
        try {
            List<String> audience = token.getAudience();
            return audience != null && audience.contains(properties.apiAudience()) ? success() : failure();
        } catch (IllegalArgumentException exception) {
            return failure();
        }
    }

    private OAuth2TokenValidatorResult validateSubject(Jwt token) {
        String subject = token.getSubject();
        return subject != null && !subject.isBlank() && subject.length() <= 512 ? success() : failure();
    }

    private OAuth2TokenValidatorResult validateDiscriminator(Jwt token) {
        Object actual = properties.discriminatorLocation() == OidcIdentityProperties.DiscriminatorLocation.HEADER
                ? token.getHeaders().get(properties.discriminatorName())
                : token.getClaims().get(properties.discriminatorName());
        return properties.discriminatorValue().equals(actual) ? success() : failure();
    }

    private OAuth2TokenValidatorResult success() {
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
