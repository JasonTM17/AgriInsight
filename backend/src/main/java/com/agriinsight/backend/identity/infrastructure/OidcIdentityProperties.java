package com.agriinsight.backend.identity.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

@ConfigurationProperties("agriinsight.identity")
public record OidcIdentityProperties(
        boolean enabled,
        String issuerUri,
        String jwkSetUri,
        String apiAudience,
        String interactiveClientId,
        Duration clockSkew,
        SignatureAlgorithm jwsAlgorithm,
        DiscriminatorLocation discriminatorLocation,
        String discriminatorName,
        String discriminatorValue,
        String displayNameClaim,
        String emailClaim,
        String assuranceClaim,
        List<String> corsAllowedOrigins) {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);
    private static final Pattern CLAIM_NAME = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public OidcIdentityProperties {
        clockSkew = clockSkew == null ? Duration.ofSeconds(60) : clockSkew;
        jwkSetUri = optionalText(jwkSetUri);
        corsAllowedOrigins = immutableOrigins(corsAllowedOrigins);
        if (enabled) {
            requireProviderUri(issuerUri, "issuer-uri");
            if (jwkSetUri != null) {
                requireProviderUri(jwkSetUri, "jwk-set-uri");
            }
            requireExactText(apiAudience, "api-audience", 256);
            requireExactText(interactiveClientId, "interactive-client-id", 256);
            if (apiAudience.equals(interactiveClientId)) {
                throw new IllegalArgumentException("api-audience must be distinct from interactive-client-id");
            }
            if (clockSkew.isNegative() || clockSkew.compareTo(MAX_CLOCK_SKEW) > 0) {
                throw new IllegalArgumentException("clock-skew must be between 0 and 2 minutes");
            }
            if (jwsAlgorithm == null) {
                throw new IllegalArgumentException("jws-algorithm is required");
            }
            if (discriminatorLocation == null) {
                throw new IllegalArgumentException("discriminator-location is required");
            }
            requireClaimName(discriminatorName, "discriminator-name");
            requireExactText(discriminatorValue, "discriminator-value", 128);
            requireClaimName(displayNameClaim, "display-name-claim");
            requireClaimName(emailClaim, "email-claim");
            requireClaimName(assuranceClaim, "assurance-claim");
            corsAllowedOrigins.forEach(OidcIdentityProperties::requireOrigin);
        }
    }

    public enum DiscriminatorLocation {
        CLAIM,
        HEADER
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> immutableOrigins(List<String> origins) {
        if (origins == null) {
            return List.of();
        }
        List<String> nonempty = origins.stream()
                .filter(Objects::nonNull)
                .filter(origin -> !origin.isBlank())
                .toList();
        Set<String> distinct = new LinkedHashSet<>(nonempty);
        if (nonempty.size() != distinct.size() || distinct.size() > 32) {
            throw new IllegalArgumentException("cors-allowed-origins must contain at most 32 unique origins");
        }
        return List.copyOf(distinct);
    }

    private static void requireProviderUri(String value, String fieldName) {
        requireExactText(value, fieldName, 2048);
        URI uri = parseUri(value, fieldName);
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException(fieldName + " must be an absolute provider URI");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        boolean localHttp = scheme.equals("http") && isLoopbackHost(uri.getHost());
        if (!scheme.equals("https") && !localHttp) {
            throw new IllegalArgumentException(fieldName + " must use HTTPS outside loopback development");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(fieldName + " must be an absolute provider URI without credentials, query, or fragment");
        }
    }

    private static void requireOrigin(String value) {
        requireExactText(value, "CORS origin", 512);
        if (value.contains("*")) {
            throw new IllegalArgumentException("CORS origin must not contain a wildcard");
        }
        URI uri = parseUri(value, "CORS origin");
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("https") && !scheme.equals("http"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("CORS origin must be an exact HTTP(S) origin without path or wildcard");
        }
    }

    private static URI parseUri(String value, String fieldName) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid URI", exception);
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }

    private static void requireClaimName(String value, String fieldName) {
        requireExactText(value, fieldName, 128);
        if (!CLAIM_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
    }

    private static void requireExactText(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be nonblank, unpadded, and at most " + maxLength + " characters");
        }
    }
}
