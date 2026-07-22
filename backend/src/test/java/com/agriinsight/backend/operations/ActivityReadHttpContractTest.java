package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ACTIVITY_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.activity;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.ActivityPage;
import com.agriinsight.backend.operations.application.ActivityService;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class ActivityReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private ActivityService activities;

    @Test
    void activityReadReturnsScopedPageAndVersionedDetail() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_READ));
        when(activities.list(any())).thenReturn(new ActivityPage(
                List.of(activity(4, ActivityStatus.STARTED)), 25, 0, false));
        when(activities.get(ACTIVITY_ID)).thenReturn(activity(4, ActivityStatus.STARTED));

        mockMvc.perform(get("/api/v1/activities")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("status", "STARTED")
                        .param("search", "north"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ACTIVITY_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("STARTED"));
        mockMvc.perform(get("/api/v1/activities/{id}", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("ACTIVITY-A"));
    }

    @Test
    void missingPermissionAndUnboundedPaginationFailBeforeService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());
        mockMvc.perform(get("/api/v1/activities").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(activities);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_READ));
        mockMvc.perform(get("/api/v1/activities")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(activities);
    }
}
