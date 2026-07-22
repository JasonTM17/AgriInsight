package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ACTIVITY_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ASSIGNMENT_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.EMPLOYEE_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.assignment;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.completedAssignment;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.stubIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.ActivityAssignmentCommandService;
import com.agriinsight.backend.operations.application.ActivityAssignmentCommands;
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
class ActivityAssignmentHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private ActivityAssignmentCommandService commands;

    @Test
    void grantUsesNewResourceVersionAndCanonicalAuditReason() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_MANAGE));
        when(commands.grant(any(), any(), any()))
                .thenReturn(completedAssignment(201, assignment(0, true)));

        mockMvc.perform(post("/api/v1/activities/{id}/assignments", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "grant-activity-assignment-1")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"" + EMPLOYEE_ID
                                + "\",\"reasonCode\":\"activity_staffing\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.activityId").value(ACTIVITY_ID.toString()))
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));

        ArgumentCaptor<ActivityAssignmentCommands.Grant> command =
                ArgumentCaptor.forClass(ActivityAssignmentCommands.Grant.class);
        verify(commands).grant(any(), any(), command.capture());
        assertThat(command.getValue().expectedVersion()).isZero();
        assertThat(command.getValue().audit().reasonCode()).contains("ACTIVITY_STAFFING");
    }

    @Test
    void revokeRequiresNestedIdentityAndReturnsIncrementedVersion() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_MANAGE));
        when(commands.revoke(any(), any(), any(), any()))
                .thenReturn(completedAssignment(200, assignment(1, false)));

        mockMvc.perform(post(
                        "/api/v1/activities/{id}/assignments/{assignmentId}/revoke",
                        ACTIVITY_ID, ASSIGNMENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "revoke-activity-assignment-1")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"assignment_revoked\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.id").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$.active").value(false));
    }
}
