package com.agriinsight.backend.identity;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.IdentityRejectedException;
import com.agriinsight.backend.identity.application.IdentityRejectionReason;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
@ExtendWith(OutputCaptureExtension.class)
class IdentitySecurityTests {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Test
    void missingAndMalformedTokensReturnRedactedProblemDetails(CapturedOutput output) throws Exception {
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

        assertThat(output)
                .contains("security.authentication_required correlationId=missing-token-01 method=GET path=/api/v1/me")
                .doesNotContain("provider key diagnostics")
                .doesNotContain("malformed-sensitive-token")
                .doesNotContain("Authorization");
    }

    @Test
    void validMappedIdentityReturnsOnlyTheEnrichedCurrentUserContract() throws Exception {
        stubActiveIdentity("valid-access-token");

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.tenantCode").value("TENANT-A"))
                .andExpect(jsonPath("$.displayName").value("Lan Nguyen"))
                .andExpect(jsonPath("$.email").value("lan@example.test"))
                .andExpect(jsonPath("$.assurance").value("mfa"))
                .andExpect(jsonPath("$.roles[0]").value("DATA_ANALYST"))
                .andExpect(jsonPath("$.permissions[0]").value("FARM_READ"))
                .andExpect(content().string(not(containsString("Provider-Subject"))))
                .andExpect(content().string(not(containsString("valid-access-token"))))
                .andExpect(content().string(not(containsString("TENANT_ADMIN"))));
    }

    @Test
    void unknownOrDisabledIdentityFailsClosed() throws Exception {
        when(jwtDecoder.decode("unknown-token")).thenReturn(jwt("unknown-token"));
        when(principalLoader.load(any()))
                .thenThrow(new IdentityRejectedException(IdentityRejectionReason.UNKNOWN_IDENTITY));

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
                .andExpect(content().string(not(containsString("Provider-Subject"))));
    }

    @Test
    void disabledProfileAndTenantFailClosed() throws Exception {
        when(jwtDecoder.decode("disabled-profile-token")).thenReturn(jwt("disabled-profile-token"));
        when(principalLoader.load(any()))
                .thenThrow(new IdentityRejectedException(IdentityRejectionReason.PROFILE_DISABLED))
                .thenThrow(new IdentityRejectedException(IdentityRejectionReason.TENANT_DISABLED));
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer disabled-profile-token"))
                .andExpect(status().isUnauthorized());

        when(jwtDecoder.decode("disabled-tenant-token")).thenReturn(jwt("disabled-tenant-token"));
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
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                PROFILE_ID,
                TENANT_ID,
                "TENANT-A",
                Optional.of("Lan Nguyen"),
                Optional.of("lan@example.test"),
                Optional.of("mfa"),
                Set.of(Role.DATA_ANALYST),
                Set.of(Permission.FARM_READ)));
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
