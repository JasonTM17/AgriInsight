package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcJwtValidatorTest {

    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String API_AUDIENCE = "agriinsight-api";

    @Test
    void acceptsOnlyTheConfiguredIssuerAudienceAndAccessTokenDiscriminator() {
        OidcJwtValidator validator = new OidcJwtValidator(properties(
                OidcIdentityProperties.DiscriminatorLocation.CLAIM,
                "token_use",
                "access"));

        assertThat(validator.validate(token(builder -> { })).hasErrors()).isFalse();
        assertThat(validator.validate(token(builder -> builder.issuer("https://wrong.example.test"))).hasErrors())
                .isTrue();
        assertThat(validator.validate(token(builder -> builder.claim("aud", "interactive-client"))).hasErrors())
                .isTrue();
        assertThat(validator.validate(token(builder -> builder.audience(List.of("other-api", "another-api"))))
                .hasErrors()).isTrue();
        assertThat(validator.validate(token(builder -> builder.claim("token_use", "id"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredFutureAndSubjectlessTokens() {
        OidcJwtValidator validator = new OidcJwtValidator(properties(
                OidcIdentityProperties.DiscriminatorLocation.CLAIM,
                "token_use",
                "access"));
        Instant now = Instant.now();

        assertThat(validator.validate(token(builder -> builder
                        .issuedAt(now.minusSeconds(180))
                        .expiresAt(now.minusSeconds(90)))).hasErrors())
                .isTrue();
        assertThat(validator.validate(token(builder -> builder.notBefore(now.plusSeconds(90)))).hasErrors())
                .isTrue();
        assertThat(validator.validate(token(builder -> builder.claim("sub", ""))).hasErrors()).isTrue();
    }

    @Test
    void supportsAnExactHeaderDiscriminator() {
        OidcJwtValidator validator = new OidcJwtValidator(properties(
                OidcIdentityProperties.DiscriminatorLocation.HEADER,
                "typ",
                "at+jwt"));

        assertThat(validator.validate(token(builder -> builder.header("typ", "at+jwt"))).hasErrors()).isFalse();
        assertThat(validator.validate(token(builder -> builder.header("typ", "JWT"))).hasErrors()).isTrue();
    }

    private Jwt token(Consumer<Jwt.Builder> customization) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("redacted-test-token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(" Provider-Subject-001 ")
                .audience(List.of(API_AUDIENCE))
                .issuedAt(now.minusSeconds(30))
                .notBefore(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access");
        customization.accept(builder);
        return builder.build();
    }

    private OidcIdentityProperties properties(
            OidcIdentityProperties.DiscriminatorLocation location,
            String discriminatorName,
            String discriminatorValue) {
        return new OidcIdentityProperties(
                true,
                ISSUER,
                "https://identity.example.test/jwks",
                API_AUDIENCE,
                "interactive-client",
                Duration.ofSeconds(30),
                SignatureAlgorithm.RS256,
                location,
                discriminatorName,
                discriminatorValue,
                "name",
                "email",
                "acr",
                List.of());
    }
}
