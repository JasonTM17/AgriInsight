package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PrincipalMapperTest {

    @Test
    void mapsOnlyConfiguredSafeClaimsIntoTheInternalPrincipal() {
        ExternalIdentityService identityService = mock(ExternalIdentityService.class);
        AgriInsightPrincipal expected = new AgriInsightPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TENANT-A",
                Optional.of("Lan"),
                Optional.of("lan@example.test"),
                Optional.of("mfa"),
                Set.of(),
                Set.of());
        when(identityService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(expected);
        PrincipalMapper mapper = new PrincipalMapper(identityService, "preferred_name", "mail", "acr");
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("never-forward-this-token")
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject(" Provider-Subject-001 ")
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(60))
                .claim("preferred_name", "Lan")
                .claim("mail", "lan@example.test")
                .claim("acr", "mfa")
                .claim("roles", List.of("TENANT_ADMIN"))
                .build();

        assertThat(mapper.map(jwt)).isSameAs(expected);
        verify(identityService).resolve(argThat(claims ->
                claims.issuer().equals("https://identity.example.test/issuer")
                        && claims.subject().equals(" Provider-Subject-001 ")
                        && claims.displayName().equals("Lan")
                        && claims.email().equals("lan@example.test")
                        && claims.assurance().equals("mfa")));
    }
}
