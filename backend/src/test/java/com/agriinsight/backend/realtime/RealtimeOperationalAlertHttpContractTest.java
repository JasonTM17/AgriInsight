package com.agriinsight.backend.realtime;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvidence;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertFeed;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertSeverity;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import com.agriinsight.backend.shared.domain.CanonicalCommandMaterial;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class RealtimeOperationalAlertHttpContractTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000072");
    private static final UUID PROFILE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000072");
    private static final UUID ALERT_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_EVENT_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID COMMAND_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000003");
    private static final Instant GENERATED_AT =
            Instant.parse("2027-09-01T03:00:00Z");
    private static final String AUTHORIZATION = "Bearer realtime-alert-token";
    private static final String ACKNOWLEDGEMENTS =
            "/api/v1/realtime/alerts/{id}/acknowledgements";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private RealtimeOperationalAlertService alerts;

    @Test
    void returnsOnlyTheFixedSafeCurrentProfileFeedShape() throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_READ));
        when(alerts.feed()).thenReturn(new RealtimeOperationalAlertFeed(
                GENERATED_AT, List.of(backlogView()), 50, false));

        mockMvc.perform(get("/api/v1/realtime/alerts")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value(GENERATED_AT.toString()))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].source").value("realtime_operational"))
                .andExpect(jsonPath("$.items[0].id").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.items[0].policy").value("OUTBOX_PUBLISH_BACKLOG"))
                .andExpect(jsonPath("$.items[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.items[0].state").value("OPEN"))
                .andExpect(jsonPath("$.items[0].evidence.type").value("TENANT_BACKLOG"))
                .andExpect(jsonPath("$.items[0].evidence.id").value(nullValue()))
                .andExpect(jsonPath("$.items[0].ageSeconds").value(30))
                .andExpect(jsonPath("$.items[0].acknowledged").value(false))
                .andExpect(jsonPath("$.items[0].acknowledgedAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].profileId").doesNotExist())
                .andExpect(jsonPath("$.items[0].dedupeKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].error").doesNotExist());
    }

    @Test
    void rejectsEveryFeedQueryKeyBeforeCallingTheService() throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_READ));

        mockMvc.perform(get("/api/v1/realtime/alerts?tenantId=" + TENANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alerts);
    }

    @Test
    void deniesMissingReadPermissionBeforeCallingTheService() throws Exception {
        stubIdentity(Set.of());

        mockMvc.perform(get("/api/v1/realtime/alerts")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(alerts);
    }

    @Test
    void acceptsOnlyEmptyJsonAndBuildsTheCanonicalAcknowledgementFingerprint()
            throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_ACKNOWLEDGE));
        RealtimeOperationalAlertView view = eventView();
        when(alerts.acknowledge(any(CommandExecutionRequest.class), eq(ALERT_ID)))
                .thenReturn(completed(Optional.of(view), false));

        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements", ALERT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.source").value("realtime_operational"))
                .andExpect(jsonPath("$.evidence.type").value("OPERATIONAL_EVENT"))
                .andExpect(jsonPath("$.evidence.id").value(SOURCE_EVENT_ID.toString()))
                .andExpect(jsonPath("$.acknowledged").value(true))
                .andExpect(jsonPath("$.acknowledgedAt")
                        .value(GENERATED_AT.minusSeconds(5).toString()))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> command =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        verify(alerts).acknowledge(command.capture(), eq(ALERT_ID));
        CanonicalCommandHasher.Fingerprint expected =
                new CanonicalCommandHasher().fingerprint(
                        CanonicalCommandHasher.CURRENT_SCHEMA_VERSION,
                        new CanonicalCommandMaterial(
                                "POST",
                                ACKNOWLEDGEMENTS,
                                Map.of("id", ALERT_ID.toString()),
                                Map.of(),
                                CanonicalCommandBody.of(Map.of()),
                                Map.of()));
        assertThat(command.getValue().fingerprint()).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownAcknowledgementFieldsAndAbsentBodies() throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_ACKNOWLEDGE));

        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements", ALERT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + TENANT_ID + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements", ALERT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements", ALERT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-null")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alerts);
    }

    @Test
    void rejectsAcknowledgementQueryKeysAndMalformedPathIdentifiers() throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_ACKNOWLEDGE));

        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements?profileId={profileId}",
                                ALERT_ID, PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/realtime/alerts/not-a-uuid/acknowledgements")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alerts);
    }

    @Test
    void mapsReplayAfterResolutionToTheSameSanitizedNotFoundContract()
            throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_ACKNOWLEDGE));
        when(alerts.acknowledge(any(CommandExecutionRequest.class), eq(ALERT_ID)))
                .thenReturn(completed(Optional.empty(), true));

        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements", ALERT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail")
                        .value("The requested resource does not exist."))
                .andExpect(jsonPath("$.alertId").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.profileId").doesNotExist());
    }

    @Test
    void deniesMissingAcknowledgePermissionBeforeCallingTheService() throws Exception {
        stubIdentity(Set.of(Permission.REALTIME_ALERT_READ));

        mockMvc.perform(post("/api/v1/realtime/alerts/{id}/acknowledgements", ALERT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "ack-alert-observation-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(alerts);
    }

    private void stubIdentity(Set<Permission> permissions) {
        when(jwtDecoder.decode("realtime-alert-token")).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                PROFILE_ID,
                TENANT_ID,
                "TENANT-REALTIME-ALERTS",
                Optional.of("Realtime alert reader"),
                Optional.empty(),
                Optional.of("mfa"),
                Set.of(Role.EXECUTIVE),
                permissions));
    }

    private CommandExecutionResult<RealtimeOperationalAlertView> completed(
            Optional<RealtimeOperationalAlertView> representation,
            boolean replayed) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                replayed,
                200,
                new CommandTarget("REALTIME_OPERATIONAL_ALERT", ALERT_ID, 7),
                representation);
    }

    private RealtimeOperationalAlertView backlogView() {
        return new RealtimeOperationalAlertView(
                ALERT_ID,
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                RealtimeOperationalAlertSeverity.WARNING,
                RealtimeOperationalAlertView.OPEN,
                RealtimeOperationalAlertEvidence.from(
                        RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, null),
                GENERATED_AT.minusSeconds(120),
                GENERATED_AT.minusSeconds(120),
                GENERATED_AT.minusSeconds(30),
                GENERATED_AT,
                30,
                false,
                Optional.empty(),
                7);
    }

    private RealtimeOperationalAlertView eventView() {
        return new RealtimeOperationalAlertView(
                ALERT_ID,
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                RealtimeOperationalAlertSeverity.CRITICAL,
                RealtimeOperationalAlertView.OPEN,
                RealtimeOperationalAlertEvidence.from(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                        SOURCE_EVENT_ID),
                GENERATED_AT.minusSeconds(120),
                GENERATED_AT.minusSeconds(120),
                GENERATED_AT.minusSeconds(30),
                GENERATED_AT,
                30,
                true,
                Optional.of(GENERATED_AT.minusSeconds(5)),
                7);
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("realtime-alert-token")
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("realtime-alert-reader")
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
