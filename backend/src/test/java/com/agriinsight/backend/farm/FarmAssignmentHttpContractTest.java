package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ASSIGNMENT_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.TARGET_PROFILE_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.assignment;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.completedAssignment;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
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
import com.agriinsight.backend.farm.application.FarmAssignmentCommandService;
import com.agriinsight.backend.farm.application.FarmAssignmentCommands;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class FarmAssignmentHttpContractTest {

    private static final String GRANT_BODY = """
            {"userProfileId":"21000000-0000-0000-0000-000000000002",
             "farmId":"31000000-0000-0000-0000-000000000001",
             "reasonCode":"manager_access"}
            """;
    private static final String REVOKE_BODY = "{\"reasonCode\":\"ACCESS_REVOKED\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private FarmAssignmentCommandService assignmentCommands;

    @Test
    void farmManagementPermissionCannotManageAssignments() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        performGrant("grant-with-wrong-permission", "\"0\"", GRANT_BODY)
                .andExpect(status().isForbidden());

        verifyNoInteractions(assignmentCommands);
    }

    @Test
    void grantAndRevokeReturnVersionedAssignmentRepresentations() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_ASSIGNMENT_MANAGE));
        when(assignmentCommands.grant(any(), any()))
                .thenReturn(completedAssignment(201, assignment(0, true)));
        when(assignmentCommands.revoke(any(), any(), any()))
                .thenReturn(completedAssignment(200, assignment(1, false)));

        performGrant("grant-farm-manager-1", "\"0\"", GRANT_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.userProfileId").value(TARGET_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.farmId").value(FARM_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/farm-assignments/{id}/revoke", FARM_ASSIGNMENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "revoke-farm-manager-1")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVOKE_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.active").value(false));

        ArgumentCaptor<FarmAssignmentCommands.Grant> grants =
                ArgumentCaptor.forClass(FarmAssignmentCommands.Grant.class);
        verify(assignmentCommands).grant(any(), grants.capture());
        assertThat(grants.getValue().userProfileId()).isEqualTo(TARGET_PROFILE_ID);
        assertThat(grants.getValue().farmId()).isEqualTo(FARM_ID);
        assertThat(grants.getValue().expectedVersion()).isZero();
        assertThat(grants.getValue().audit().reasonCode()).contains("MANAGER_ACCESS");
    }

    @Test
    void malformedBodiesAndWeakVersionsFailBeforeCommandService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_ASSIGNMENT_MANAGE));

        performGrant(
                        "unknown-farm-assignment-field",
                        "\"0\"",
                        GRANT_BODY.replace("}", ",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        performGrant("weak-farm-assignment-version", "W/\"0\"", GRANT_BODY)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(assignmentCommands);
    }

    private org.springframework.test.web.servlet.ResultActions performGrant(
            String key,
            String ifMatch,
            String body) throws Exception {
        return mockMvc.perform(post("/api/v1/farm-assignments")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .header("Idempotency-Key", key)
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
