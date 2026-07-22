package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.COMMAND_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.WAREHOUSE_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.stubIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentCommandService;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentCommands;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentRecord;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class WarehouseAssignmentHttpContractTest {

    private static final UUID PROFILE_ID = UUID.fromString("21000000-0000-0000-0000-000000000002");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("55000000-0000-0000-0000-000000000001");
    private static final String GRANT_BODY = """
            {"userProfileId":"21000000-0000-0000-0000-000000000002",
             "warehouseId":"51000000-0000-0000-0000-000000000001",
             "reasonCode":"warehouse_access"}
            """;
    private static final String REVOKE_BODY = "{\"reasonCode\":\"ACCESS_REVOKED\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private WarehouseAssignmentCommandService commands;

    @Test
    void inventoryManagementPermissionCannotManageAssignments() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));

        performGrant("grant-with-wrong-permission", "\"0\"", GRANT_BODY)
                .andExpect(status().isForbidden());

        verifyNoInteractions(commands);
    }

    @Test
    void grantAndRevokeReturnSafeVersionedRepresentations() throws Exception {
        stubIdentity(
                jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_ASSIGNMENT_MANAGE));
        when(commands.grant(any(), any())).thenReturn(completed(201, assignment(0, true)));
        when(commands.revoke(any(), any(), any()))
                .thenReturn(completed(200, assignment(1, false)));

        performGrant("grant-warehouse-manager-1", "\"0\"", GRANT_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.userProfileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.warehouseId").value(WAREHOUSE_ID.toString()))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/warehouse-assignments/{id}/revoke", ASSIGNMENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "revoke-warehouse-manager-1")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVOKE_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.active").value(false));

        ArgumentCaptor<WarehouseAssignmentCommands.Grant> grants =
                ArgumentCaptor.forClass(WarehouseAssignmentCommands.Grant.class);
        verify(commands).grant(any(), grants.capture());
        assertThat(grants.getValue().userProfileId()).isEqualTo(PROFILE_ID);
        assertThat(grants.getValue().warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(grants.getValue().expectedVersion()).isZero();
        assertThat(grants.getValue().audit().reasonCode()).contains("WAREHOUSE_ACCESS");
    }

    @Test
    void malformedBodiesAndWeakVersionsFailBeforeCommandService() throws Exception {
        stubIdentity(
                jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_ASSIGNMENT_MANAGE));

        performGrant(
                        "unknown-warehouse-assignment-field",
                        "\"0\"",
                        GRANT_BODY.replace("}", ",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        performGrant("weak-warehouse-assignment-version", "W/\"0\"", GRANT_BODY)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commands);
    }

    private org.springframework.test.web.servlet.ResultActions performGrant(
            String key,
            String ifMatch,
            String body) throws Exception {
        return mockMvc.perform(post("/api/v1/warehouse-assignments")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .header("Idempotency-Key", key)
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private WarehouseAssignmentRecord assignment(long version, boolean active) {
        return new WarehouseAssignmentRecord(
                ASSIGNMENT_ID,
                TENANT_ID,
                PROFILE_ID,
                WAREHOUSE_ID,
                active ? Optional.empty() : Optional.of(Instant.parse("2026-07-22T06:00:00Z")),
                version);
    }

    private CommandExecutionResult.Completed<WarehouseAssignmentRecord> completed(
            int status,
            WarehouseAssignmentRecord assignment) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                false,
                status,
                new CommandTarget(
                        "WAREHOUSE_ASSIGNMENT", assignment.id(), assignment.version()),
                Optional.of(assignment));
    }
}
