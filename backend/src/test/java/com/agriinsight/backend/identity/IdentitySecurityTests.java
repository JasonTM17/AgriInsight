package com.agriinsight.backend.identity;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.identity.application.IdentityBootstrap;
import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class IdentitySecurityTests {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityBootstrapPort bootstrapPort;

    @Test
    void missingAndMalformedTokensReturnRedactedProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Correlation-Id", "missing-token-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "missing-token-01"))
                .andExpect(jsonPath("$.title").value("Authentication required"));

        when(jwtDecoder.decode("malformed-sensitive-token"))
                .thenThrow(new BadJwtException("provider key diagnostics must stay private"));
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-sensitive-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(not(containsString("provider key diagnostics"))))
                .andExpect(content().string(not(containsString("malformed-sensitive-token"))));
    }

    @Test
    void validMappedIdentityCanReadOnlyTheMinimumCurrentUserContract() throws Exception {
        stubActiveIdentity("valid-access-token");

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("Lan Nguyen"))
                .andExpect(jsonPath("$.email").value("lan@example.test"))
                .andExpect(jsonPath("$.assurance").value("mfa"))
                .andExpect(content().string(not(containsString("Provider-Subject"))))
                .andExpect(content().string(not(containsString("valid-access-token"))))
                .andExpect(content().string(not(containsString("TENANT_ADMIN"))));
    }

    @Test
    void unknownOrDisabledIdentityFailsClosed() throws Exception {
        when(jwtDecoder.decode("unknown-token")).thenReturn(jwt("unknown-token"));
        when(bootstrapPort.findByIssuerAndSubject(
                "https://identity.example.test/issuer",
                " Provider-Subject-001 ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
                .andExpect(content().string(not(containsString("Provider-Subject"))));
    }

    @Test
    void disabledProfileAndTenantFailClosed() throws Exception {
        when(jwtDecoder.decode("disabled-profile-token")).thenReturn(jwt("disabled-profile-token"));
        when(bootstrapPort.findByIssuerAndSubject(
                "https://identity.example.test/issuer",
                " Provider-Subject-001 ")).thenReturn(Optional.of(
                        new IdentityBootstrap(PROFILE_ID, TENANT_ID, false, true)));
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer disabled-profile-token"))
                .andExpect(status().isUnauthorized());

        when(jwtDecoder.decode("disabled-tenant-token")).thenReturn(jwt("disabled-tenant-token"));
        when(bootstrapPort.findByIssuerAndSubject(
                "https://identity.example.test/issuer",
                " Provider-Subject-001 ")).thenReturn(Optional.of(
                        new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, false)));
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer disabled-tenant-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtRolesCannotOpenAnUnregisteredBusinessRoute() throws Exception {
        stubActiveIdentity("role-claim-token");

        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer role-claim-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(content().string(not(containsString("TENANT_ADMIN"))));
    }

    @Test
    void enabledApiDocsRemainPrivateOutsideDevelopmentProfiles() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void healthAndExactCorsOriginRemainPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        verifyNoInteractions(jwtDecoder);

        mockMvc.perform(options("/api/v1/me")
                        .header(HttpHeaders.ORIGIN, "https://app.agriinsight.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.agriinsight.test"));
        mockMvc.perform(options("/api/v1/me")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private void stubActiveIdentity(String tokenValue) {
        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt(tokenValue));
        when(bootstrapPort.findByIssuerAndSubject(
                "https://identity.example.test/issuer",
                " Provider-Subject-001 ")).thenReturn(Optional.of(
                        new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, true)));
    }

    private Jwt jwt(String tokenValue) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject(" Provider-Subject-001 ")
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .notBefore(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .claim("name", "Lan Nguyen")
                .claim("email", "lan@example.test")
                .claim("acr", "mfa")
                .claim("roles", List.of("TENANT_ADMIN"))
                .build();
    }
}
