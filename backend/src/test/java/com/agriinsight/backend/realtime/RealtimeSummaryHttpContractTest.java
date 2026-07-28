package com.agriinsight.backend.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.realtime.application.RealtimeMetric;
import com.agriinsight.backend.realtime.application.RealtimeSummary;
import com.agriinsight.backend.realtime.application.RealtimeSummaryService;
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
class RealtimeSummaryHttpContractTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000051");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000051");
    private static final String AUTHORIZATION = "Bearer realtime-summary-token";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private RealtimeSummaryService summaries;

    @Test
    void returnsOnlyBoundedPayloadFreeMetricsToRealtimeReaders() throws Exception {
        stubIdentity(Role.DATA_ANALYST, Set.of(Permission.REALTIME_READ));
        when(summaries.summarize()).thenReturn(summary());

        mockMvc.perform(get("/api/v1/realtime/summary")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lens").value("REALTIME_OPERATIONAL"))
                .andExpect(jsonPath("$.source").value("KAFKA_READ_MODEL"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.eventCount").value(12))
                .andExpect(jsonPath("$.freshnessSeconds").value(7))
                .andExpect(jsonPath("$.items[0].eventType")
                        .value("AGRIINSIGHT.OPERATIONAL.ACTIVITY.COMMITTED"))
                .andExpect(jsonPath("$.items[0].checksum").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload").doesNotExist());
    }

    @Test
    void deniesRolesWithoutRealtimeReadBeforeInvokingTheSummary() throws Exception {
        stubIdentity(Role.FARM_MANAGER, Set.of());

        mockMvc.perform(get("/api/v1/realtime/summary")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(summaries);
    }

    private void stubIdentity(Role role, Set<Permission> permissions) {
        when(jwtDecoder.decode("realtime-summary-token")).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                PROFILE_ID, TENANT_ID, "TENANT-REALTIME", Optional.of("Realtime reader"),
                Optional.empty(), Optional.of("mfa"), Set.of(role), permissions));
    }

    private RealtimeSummary summary() {
        Instant occurredAt = Instant.parse("2027-09-01T02:00:00Z");
        Instant processedAt = Instant.parse("2027-09-01T02:00:07Z");
        return new RealtimeSummary(
                TENANT_ID, 12, Optional.of(occurredAt), Optional.of(processedAt), 7,
                List.of(new RealtimeMetric(
                        "AGRIINSIGHT.OPERATIONAL.ACTIVITY.COMMITTED", "ACTIVITY", 12,
                        occurredAt, processedAt)),
                100, false);
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("realtime-summary-token")
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("realtime-reader")
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
