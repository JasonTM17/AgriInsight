package com.agriinsight.backend.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.identity.application.TenantUserPage;
import com.agriinsight.backend.identity.application.TenantUserProfile;
import com.agriinsight.backend.identity.application.TenantUserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class TenantUserReadHttpContractTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String AUTHORIZATION = "Bearer tenant-reader-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private TenantUserService tenantUsers;

    @Test
    void userManagementPermissionReadsOnlyTheBoundedTenantContract() throws Exception {
        stubIdentity(Set.of(Permission.IDENTITY_USER_MANAGE));
        TenantUserProfile profile = profile();
        when(tenantUsers.list(any())).thenReturn(new TenantUserPage(List.of(profile), 25, 0, false));
        when(tenantUsers.get(PROFILE_ID)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/v1/users/{id}", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void identityWithoutUserManagementPermissionCannotEnumerateUsers() throws Exception {
        stubIdentity(Set.of());

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tenantUsers);
    }

    private void stubIdentity(Set<Permission> permissions) {
        when(jwtDecoder.decode("tenant-reader-token")).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID,
                TENANT_ID,
                "TENANT-A",
                Optional.of("Admin"),
                Optional.empty(),
                Optional.of("mfa"),
                Set.of(Role.TENANT_ADMIN),
                permissions));
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("tenant-reader-token")
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("provider-admin")
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }

    private TenantUserProfile profile() {
        return new TenantUserProfile(
                PROFILE_ID,
                TENANT_ID,
                "Mai Tran",
                Optional.of("mai@example.test"),
                true,
                4);
    }
}
