package com.agriinsight.backend.operations;

import static com.agriinsight.backend.operations.ActivityHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.COMMAND_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.FIELD_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.SEASON_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.operations.ActivityHttpTestSupport.stubIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.HarvestCommandService;
import com.agriinsight.backend.operations.application.HarvestCommands;
import com.agriinsight.backend.operations.application.HarvestPage;
import com.agriinsight.backend.operations.application.HarvestRecord;
import com.agriinsight.backend.operations.application.HarvestService;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class HarvestHttpContractTest {

    private static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    private static final UUID HARVEST_ID = UUID.fromString("64000000-0000-0000-0000-000000000001");
    private static final UUID ORIGINAL_ID = UUID.fromString("64000000-0000-0000-0000-000000000002");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private HarvestService harvests;
    @Autowired private HarvestCommandService commands;

    @Test
    void readRoutesReturnBoundedFactsWithoutTenantId() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.HARVEST_READ));
        HarvestRecord fact = harvest(false);
        when(harvests.list(any())).thenReturn(new HarvestPage(List.of(fact), 25, 0, false));
        when(harvests.get(HARVEST_ID)).thenReturn(fact);

        mockMvc.perform(get("/api/v1/harvests")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("farmId", FARM_ID.toString())
                        .param("occurredFrom", "2027-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(HARVEST_ID.toString()))
                .andExpect(jsonPath("$.items[0].quantityKg").value(1250))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
        mockMvc.perform(get("/api/v1/harvests/{id}", HARVEST_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""));
    }

    @Test
    void postNormalizesTonnesBeforeCommandExecution() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.HARVEST_MANAGE));
        when(commands.post(any(), any())).thenReturn(completed(harvest(false)));

        mockMvc.perform(post("/api/v1/harvests")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "post-harvest-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"farmId":"%s","fieldId":"%s","seasonId":"%s","cropId":"%s",
                                 "occurredOn":"2027-09-03","quantity":1.250,"wasteQuantity":0.025,
                                 "unit":"TONNE","qualityGrade":" A ","revenueVnd":30000000,
                                 "reasonCode":"harvest_post"}
                                """.formatted(FARM_ID, FIELD_ID, SEASON_ID, CROP_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/harvests/" + HARVEST_ID))
                .andExpect(jsonPath("$.quantityKg").value(1250));

        ArgumentCaptor<HarvestCommands.Post> command =
                ArgumentCaptor.forClass(HarvestCommands.Post.class);
        verify(commands).post(any(), command.capture());
        assertThat(command.getValue().quantityKg()).isEqualByComparingTo("1250");
        assertThat(command.getValue().wasteQuantityKg()).isEqualByComparingTo("25");
        assertThat(command.getValue().qualityGrade()).contains("A");
        assertThat(command.getValue().audit().reasonCode()).contains("HARVEST_POST");
    }

    @Test
    void voidCorrectionAndSecurityBoundaryAreExact() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.HARVEST_MANAGE));
        when(commands.correct(any(), any(), any())).thenReturn(completed(harvest(true)));

        mockMvc.perform(post("/api/v1/harvests/{id}/corrections", ORIGINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "void-harvest-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correctionKind":"VOID","occurredOn":"2027-09-03",
                                 "correctionReason":"Duplicate entry","reasonCode":"harvest_void"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctionKind").value("VOID"))
                .andExpect(jsonPath("$.quantityKg").value(0));

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.HARVEST_READ));
        mockMvc.perform(post("/api/v1/harvests")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "post-harvest-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPaginationFailsBeforeReadService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.HARVEST_READ));
        mockMvc.perform(get("/api/v1/harvests")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(harvests);
    }

    private HarvestRecord harvest(boolean correction) {
        return new HarvestRecord(
                HARVEST_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID, ACTOR_ID,
                LocalDate.parse("2027-09-03"),
                correction ? BigDecimal.ZERO : new BigDecimal("1250"),
                correction ? BigDecimal.ZERO : new BigDecimal("25"),
                correction ? Optional.empty() : Optional.of("A"),
                correction ? Optional.empty() : Optional.of(new BigDecimal("30000000")),
                correction ? Optional.of(ORIGINAL_ID) : Optional.empty(),
                correction ? Optional.of(HarvestCorrectionKind.VOID) : Optional.empty(),
                correction ? Optional.of("Duplicate entry") : Optional.empty(), 0);
    }

    private CommandExecutionResult.Completed<HarvestRecord> completed(HarvestRecord harvest) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID, false, 201,
                new CommandTarget("HARVEST", harvest.id(), harvest.version()), Optional.of(harvest));
    }
}
