package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ACTIVITY_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ASSIGNMENT_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.LOG_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.activityLog;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.assignment;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.ActivityAssignmentPage;
import com.agriinsight.backend.operations.application.ActivityAssignmentReadService;
import com.agriinsight.backend.operations.application.ActivityLogPage;
import com.agriinsight.backend.operations.application.ActivityLogReadService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class ActivityReadCollectionsHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private ActivityAssignmentReadService assignments;
    @Autowired private ActivityLogReadService logs;

    @Test
    void activityReadReturnsBoundedAssignmentLogAndHistoryPagesWithoutTenantIds() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_READ));
        when(assignments.list(any(), any())).thenReturn(
                new ActivityAssignmentPage(List.of(assignment(0, true)), 25, 0, false));
        when(logs.list(any(), any())).thenReturn(
                new ActivityLogPage(List.of(activityLog(false)), 25, 0, false));
        when(logs.history(any(), any(), any())).thenReturn(
                new ActivityLogPage(List.of(activityLog(false)), 25, 0, false));

        mockMvc.perform(get("/api/v1/activities/{id}/assignments", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/v1/activities/{id}/logs", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(LOG_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());

        mockMvc.perform(get(
                        "/api/v1/activities/{id}/logs/{logId}/history",
                        ACTIVITY_ID,
                        LOG_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(LOG_ID.toString()));
    }

    @Test
    void appendPermissionCannotReadLogsAndInvalidBoundsFailBeforeReadService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_LOG_APPEND));

        mockMvc.perform(get("/api/v1/activities/{id}/logs", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(logs);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_READ));
        mockMvc.perform(get("/api/v1/activities/{id}/assignments", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }
}
