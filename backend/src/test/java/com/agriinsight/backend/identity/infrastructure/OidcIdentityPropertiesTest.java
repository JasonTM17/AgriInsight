package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

class OidcIdentityPropertiesTest {

    @Test
    void acceptsACompleteProviderNeutralAccessTokenContract() {
        OidcIdentityProperties properties = validProperties();

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.apiAudience()).isEqualTo("agriinsight-api");
        assertThat(properties.corsAllowedOrigins()).containsExactly("https://app.agriinsight.test");
    }

    @Test
    void rejectsAmbiguousTokenAndBrowserContracts() {
        assertThatThrownBy(() -> properties(
                "interactive-client",
                "interactive-client",
                List.of("https://app.agriinsight.test"),
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct");
        assertThatThrownBy(() -> properties(
                "agriinsight-api",
                "interactive-client",
                List.of("*"),
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin");
        assertThatThrownBy(() -> properties(
                "agriinsight-api",
                "interactive-client",
                List.of("https://app.agriinsight.test/path"),
                Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin");
    }

    @Test
    void rejectsUnboundedClockSkewAndUnsafeProviderUris() {
        assertThatThrownBy(() -> properties(
                "agriinsight-api",
                "interactive-client",
                List.of(),
                Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clock-skew");
        assertThatThrownBy(() -> new OidcIdentityProperties(
                true,
                "http://identity.example.test/issuer",
                null,
                "agriinsight-api",
                "interactive-client",
                Duration.ofSeconds(30),
                SignatureAlgorithm.RS256,
                OidcIdentityProperties.DiscriminatorLocation.CLAIM,
                "token_use",
                "access",
                "name",
                "email",
                "acr",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new OidcIdentityProperties(
                true,
                "identity.example.test/issuer",
                null,
                "agriinsight-api",
                "interactive-client",
                Duration.ofSeconds(30),
                SignatureAlgorithm.RS256,
                OidcIdentityProperties.DiscriminatorLocation.CLAIM,
                "token_use",
                "access",
                "name",
                "email",
                "acr",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute provider URI");
    }

    @Test
    void disabledBoundaryDoesNotRequireProviderConfiguration() {
        OidcIdentityProperties properties = new OidcIdentityProperties(
                false, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.corsAllowedOrigins()).isEmpty();
    }

    @Test
    void enabledBoundaryFailsApplicationStartupWhenProviderContractIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesBindingConfiguration.class)
                .withPropertyValues("agriinsight.identity.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("issuer-uri is required");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OidcIdentityProperties.class)
    static class PropertiesBindingConfiguration {
    }

    private OidcIdentityProperties validProperties() {
        return properties(
                "agriinsight-api",
                "interactive-client",
                List.of("https://app.agriinsight.test"),
                Duration.ofSeconds(30));
    }

    private OidcIdentityProperties properties(
            String apiAudience,
            String interactiveClientId,
            List<String> origins,
            Duration clockSkew) {
        return new OidcIdentityProperties(
                true,
                "https://identity.example.test/issuer",
                "https://identity.example.test/jwks",
                apiAudience,
                interactiveClientId,
                clockSkew,
                SignatureAlgorithm.RS256,
                OidcIdentityProperties.DiscriminatorLocation.CLAIM,
                "token_use",
                "access",
                "name",
                "email",
                "acr",
                origins);
    }
}
