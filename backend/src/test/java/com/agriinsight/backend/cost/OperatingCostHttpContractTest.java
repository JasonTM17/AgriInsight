package com.agriinsight.backend.cost;

import static com.agriinsight.backend.cost.CostHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.cost.CostHttpTestSupport.ORIGINAL_ID;
import static com.agriinsight.backend.cost.CostHttpTestSupport.REPLACEMENT_ID;
import static com.agriinsight.backend.cost.CostHttpTestSupport.SEASON_ID;
import static com.agriinsight.backend.cost.CostHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.cost.CostHttpTestSupport.completedCorrection;
import static com.agriinsight.backend.cost.CostHttpTestSupport.completedPosting;
import static com.agriinsight.backend.cost.CostHttpTestSupport.posting;
import static com.agriinsight.backend.cost.CostHttpTestSupport.stubIdentity;
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
import com.agriinsight.backend.cost.application.CostCommands;
import com.agriinsight.backend.cost.application.CostSummaryGroup;
import com.agriinsight.backend.cost.application.CostSummaryItem;
import com.agriinsight.backend.cost.application.CostSummaryResult;
import com.agriinsight.backend.cost.application.OperatingCostCommandService;
import com.agriinsight.backend.cost.application.OperatingCostPage;
import com.agriinsight.backend.cost.application.OperatingCostQueryService;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class OperatingCostHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private OperatingCostCommandService commands;
    @Autowired private OperatingCostQueryService costs;

    @Test
    void readRoutesExposeBoundedLedgerAndExplicitOperatingCostLens() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.COST_READ));
        when(costs.list(any())).thenReturn(new OperatingCostPage(
                List.of(posting()), 50, 0, false));
        when(costs.get(REPLACEMENT_ID)).thenReturn(posting());
        when(costs.summarize(any())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/cost-entries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("occurredFrom", "2027-09-01T00:00:00Z")
                        .param("occurredTo", "2027-10-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].signedAmountVnd").value(1150000))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
        mockMvc.perform(get("/api/v1/cost-entries/{id}", REPLACEMENT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""));
        mockMvc.perform(get("/api/v1/cost-summaries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("occurredFrom", "2027-09-01T00:00:00Z")
                        .param("occurredTo", "2027-10-01T00:00:00Z")
                        .param("groupBy", "SEASON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lens").value("OPERATING_COST"))
                .andExpect(jsonPath("$.source").value("OPERATING_COST_LEDGER"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.items[0].budgetVarianceVnd").value(850000));
    }

    @Test
    void postingNormalizesAuditAndRejectsInventoryLinkage() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.COST_MANAGE));
        when(commands.post(any(), any())).thenReturn(completedPosting());

        mockMvc.perform(post("/api/v1/cost-entries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "post-cost-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("")))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION, "/api/v1/cost-entries/" + REPLACEMENT_ID));

        ArgumentCaptor<CostCommands.Post> command = ArgumentCaptor.forClass(CostCommands.Post.class);
        verify(commands).post(any(), command.capture());
        assertThat(command.getValue().target().id()).contains(SEASON_ID);
        assertThat(command.getValue().audit().reasonCode()).contains("MONTHLY_POSTING");

        mockMvc.perform(post("/api/v1/cost-entries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "post-cost-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(",\"inventoryTransactionId\":\"" + REPLACEMENT_ID + "\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correctionReturnsBothImmutableRowsAndReadPermissionCannotWrite() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.COST_MANAGE));
        when(commands.correct(any(), eq(ORIGINAL_ID), any()))
                .thenReturn(completedCorrection());
        mockMvc.perform(post("/api/v1/cost-entries/{id}/corrections", ORIGINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "correct-cost-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversal.reversalOf").value(ORIGINAL_ID.toString()))
                .andExpect(jsonPath("$.reversal.signedAmountVnd").value(-1200000))
                .andExpect(jsonPath("$.replacement.id").value(REPLACEMENT_ID.toString()));

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.COST_READ));
        mockMvc.perform(post("/api/v1/cost-entries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "post-cost-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOrOversizedReadPeriodFailsBeforeService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.COST_READ));
        mockMvc.perform(get("/api/v1/cost-entries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/cost-entries")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("occurredFrom", "2027-01-01T00:00:00Z")
                        .param("occurredTo", "2028-01-03T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(costs);
    }

    private CostSummaryResult summary() {
        return new CostSummaryResult(
                TENANT_ID,
                Instant.parse("2027-09-01T00:00:00Z"),
                Instant.parse("2027-10-01T00:00:00Z"),
                CostSummaryGroup.SEASON,
                List.of(new CostSummaryItem(
                        Optional.of(SEASON_ID), "SEASON-A", new BigDecimal("1150000"),
                        BigDecimal.ZERO, new BigDecimal("1150000"),
                        Optional.of(new BigDecimal("2000000")),
                        Optional.of(new BigDecimal("850000")))),
                500,
                false);
    }

    private String postBody(String extra) {
        return """
                {"targetType":"SEASON","targetId":"%s","category":"LABOR",
                 "amountVnd":1150000,"occurredAt":"2027-09-01T02:00:00Z",
                 "description":" Seasonal labor ","sourceReference":" PAYROLL-09 ",
                 "reasonCode":"monthly_posting"%s}
                """.formatted(SEASON_ID, extra);
    }

    private String correctionBody() {
        return """
                {"targetType":"SEASON","targetId":"%s","category":"LABOR",
                 "amountVnd":1150000,"occurredAt":"2027-09-01T02:00:00Z",
                 "description":"Corrected labor","sourceReference":"PAYROLL-09-R1",
                 "correctionReason":"Correct invoice allocation"}
                """.formatted(SEASON_ID);
    }
}
