package com.agriinsight.backend.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.application.TenantRoleAssignment;
import com.agriinsight.backend.authorization.application.TenantRoleCommandService;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class TenantRoleHttpSecurityContractTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("22000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private TenantRoleCommandService roleCommands;

    @Test
    void userManagementPermissionCannotMutateRoles() throws Exception {
        stubIdentity(Set.of(Permission.IDENTITY_USER_MANAGE));

        performGrant().andExpect(status().isForbidden());

        verifyNoInteractions(roleCommands);
    }

    @Test
    void roleManagementPermissionCanGrantOnlyAFixedRoleWithVersionPrecondition() throws Exception {
        stubIdentity(Set.of(Permission.IDENTITY_ROLE_MANAGE));
        TenantRoleAssignment assignment = new TenantRoleAssignment(
                ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, Role.DATA_ANALYST, true, 0);
        when(roleCommands.grant(any(), any(), any())).thenReturn(new CommandExecutionResult.Completed<>(
                UUID.fromString("23000000-0000-0000-0000-000000000002"),
                false,
                200,
                new CommandTarget("USER_ROLE", ASSIGNMENT_ID, 0),
                Optional.of(assignment)));

        performGrant()
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.roleCode").value("DATA_ANALYST"));

        mockMvc.perform(post("/api/v1/users/{id}/roles", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer role-admin-token")
                        .header("Idempotency-Key", "grant-unknown-role")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayEtagMatchesTheCurrentlyAuthorizedRepresentation() throws Exception {
        stubIdentity(Set.of(Permission.IDENTITY_ROLE_MANAGE));
        TenantRoleAssignment current = new TenantRoleAssignment(
                ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, Role.DATA_ANALYST, true, 3);
        when(roleCommands.grant(any(), any(), any())).thenReturn(new CommandExecutionResult.Completed<>(
                UUID.fromString("23000000-0000-0000-0000-000000000003"),
                true,
                200,
                new CommandTarget("USER_ROLE", ASSIGNMENT_ID, 0),
                Optional.of(current)));

        performGrant()
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.version").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions performGrant() throws Exception {
        return mockMvc.perform(post("/api/v1/users/{id}/roles", PROFILE_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer role-admin-token")
                .header("Idempotency-Key", "grant-data-analyst")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"DATA_ANALYST\"}"));
    }

    private void stubIdentity(Set<Permission> permissions) {
        when(jwtDecoder.decode("role-admin-token")).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID, TENANT_ID, "TENANT-A", Optional.of("Admin"), Optional.empty(), Optional.of("mfa"),
                Set.of(Role.TENANT_ADMIN), permissions));
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("role-admin-token")
                .header("alg", "RS256").issuer("https://identity.example.test/issuer")
                .subject("provider-admin").audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30)).expiresAt(now.plusSeconds(90))
                .claim("token_use", "access").build();
    }
}
