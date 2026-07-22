package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ACTIVITY_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.EMPLOYEE_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.LOG_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.activityLog;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.completedLog;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.stubIdentity;
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
import com.agriinsight.backend.operations.application.ActivityLogCommandService;
import com.agriinsight.backend.operations.application.ActivityLogCommands;
import java.math.BigDecimal;
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
class ActivityLogHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private ActivityLogCommandService commands;

    @Test
    void appendCanonicalizesPayloadAndDoesNotExposeTenant() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_LOG_APPEND));
        when(commands.append(any(), any(), any())).thenReturn(completedLog(activityLog(false)));

        mockMvc.perform(post("/api/v1/activities/{id}/logs", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "append-activity-log-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":"%s","occurredAt":"2027-01-01T01:00:00Z",
                                 "notes":"  Harvested  ","quantity":100.0000,"unit":"KG",
                                 "reasonCode":"field_work"}
                                """.formatted(EMPLOYEE_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/api/v1/activities/" + ACTIVITY_ID + "/logs/" + LOG_ID))
                .andExpect(jsonPath("$.activityId").value(ACTIVITY_ID.toString()))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<ActivityLogCommands.Append> command =
                ArgumentCaptor.forClass(ActivityLogCommands.Append.class);
        verify(commands).append(any(), any(), command.capture());
        assertThat(command.getValue().notes()).contains("Harvested");
        assertThat(command.getValue().quantity()).contains(new BigDecimal("1E+2"));
        assertThat(command.getValue().audit().reasonCode()).contains("FIELD_WORK");
    }

    @Test
    void voidCorrectionUsesNestedIdentityAndEmptyMeasuredPayload() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_LOG_APPEND));
        when(commands.correct(any(), any(), any(), any()))
                .thenReturn(completedLog(activityLog(true)));
        UUID originalId = UUID.fromString("62000000-0000-0000-0000-000000000002");

        mockMvc.perform(post(
                        "/api/v1/activities/{id}/logs/{logId}/corrections",
                        ACTIVITY_ID, originalId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "correct-activity-log-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correctionKind":"VOID","occurredAt":"2027-01-01T02:00:00Z",
                                 "notes":"Voided","correctionReason":"Duplicate entry",
                                 "reasonCode":"log_correction"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctsLogId").value(originalId.toString()))
                .andExpect(jsonPath("$.correctionKind").value("VOID"))
                .andExpect(jsonPath("$.quantity").doesNotExist());

        ArgumentCaptor<ActivityLogCommands.Correct> command =
                ArgumentCaptor.forClass(ActivityLogCommands.Correct.class);
        verify(commands).correct(any(), any(), any(), command.capture());
        assertThat(command.getValue().correctionReason()).isEqualTo("Duplicate entry");
        assertThat(command.getValue().quantity()).isEmpty();
    }

    @Test
    void wrongPermissionAndInvalidEvidenceFailBeforeCommandExecution() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_MANAGE));
        mockMvc.perform(post("/api/v1/activities/{id}/logs", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "append-activity-log-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppendBody("https://evidence.example/log-1")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commands);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.ACTIVITY_LOG_APPEND));
        mockMvc.perform(post("/api/v1/activities/{id}/logs", ACTIVITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "append-activity-log-invalid-evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppendBody("file:///etc/passwd")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(commands);
    }

    private String validAppendBody(String evidenceUri) {
        return """
                {"employeeId":"%s","occurredAt":"2027-01-01T01:00:00Z",
                 "notes":"Harvested","evidenceUri":"%s"}
                """.formatted(EMPLOYEE_ID, evidenceUri);
    }
}
