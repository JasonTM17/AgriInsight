package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ACTIVITY_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.activity;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.completed;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.ActivityCommandService;
import com.agriinsight.backend.operations.application.ActivityCommands;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import java.time.Instant;
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
class ActivityMutationHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private ActivityCommandService commands;

    @Test
    void invalidPatchAndTransitionFailBeforeService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_MANAGE));
        mockMvc.perform(patch("/api/v1/activities/{id}", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "invalid-activity-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"set\",\"clearDescription\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/activities/{id}/transition", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "invalid-activity-transition")
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"STARTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchUsesIfMatchAndPreservesExplicitDescriptionClear() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_MANAGE));
        when(commands.update(any(), any(), any()))
                .thenReturn(completed(200, activity(3, ActivityStatus.PLANNED)));

        mockMvc.perform(patch("/api/v1/activities/{id}", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "patch-activity-1")
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearDescription\":true,\"reasonCode\":\"activity_change\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.description").value("Notes"));

        ArgumentCaptor<ActivityCommands.Update> command = ArgumentCaptor.forClass(ActivityCommands.Update.class);
        verify(commands).update(any(), any(), command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().description())
                .contains(java.util.Optional.empty());
    }

    @Test
    void transitionCanonicalizesEffectiveTimeAndReason() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_MANAGE));
        when(commands.transition(any(), any(), any()))
                .thenReturn(completed(200, activity(1, ActivityStatus.STARTED)));

        mockMvc.perform(post("/api/v1/activities/{id}/transition", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "transition-activity-1")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"STARTED\",\"effectiveAt\":\"2027-01-01T01:00:00Z\",\"reasonCode\":\"activity_start\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        ArgumentCaptor<ActivityCommands.Transition> command = ArgumentCaptor.forClass(ActivityCommands.Transition.class);
        verify(commands).transition(any(), any(), command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().targetStatus())
                .isEqualTo(ActivityStatus.STARTED);
        org.assertj.core.api.Assertions.assertThat(command.getValue().audit().reasonCode())
                .contains("ACTIVITY_START");
        org.assertj.core.api.Assertions.assertThat(command.getValue().effectiveAt())
                .isEqualTo(Instant.parse("2027-01-01T01:00:00Z"));
    }
}
