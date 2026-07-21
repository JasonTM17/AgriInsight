package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.CROP_ID;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.FIELD_ID;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.SEASON_ID;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.completed;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.season;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.SeasonCommandService;
import com.agriinsight.backend.farm.application.SeasonCommands;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@IdentitySecurityContext
class SeasonCreateUpdateHttpContractTest {

    private static final String CREATE_BODY = """
            {"farmId":"%s","fieldId":"%s","cropId":"%s","code":" season-a ",
             "displayName":" Season A ","varietyName":" Arabica ",
             "plannedStartDate":"2027-01-01","plannedEndDate":"2027-12-31",
             "plantedAreaHectares":10.0000,"budgetVnd":1000000.00}
            """.formatted(FARM_ID, FIELD_ID, CROP_ID);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private SeasonCommandService seasonCommands;

    @Test
    void explicitClearFlagsDeserializeAsARealPatch() throws Exception {
        var request = objectMapper.readValue(
                "{\"clearBudgetVnd\":true,\"reasonCode\":\"season_change\"}",
                com.agriinsight.backend.farm.api.SeasonUpdateRequest.class);

        assertThat(request.clearBudgetVnd()).isTrue();
        assertThat(request.reasonCode()).isEqualTo("SEASON_CHANGE");
    }

    @Test
    void invalidMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_MANAGE));

        mockMvc.perform(post("/api/v1/seasons")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/seasons/{id}", SEASON_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-season-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Season\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/seasons")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-season-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.stripTrailing().replace("}", ",\"tenantId\":\"" + TENANT_ID + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(seasonCommands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_MANAGE));
        when(seasonCommands.create(any(), any()))
                .thenReturn(completed(201, season(0, Season.Status.PLANNED)));

        mockMvc.perform(post("/api/v1/seasons")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-season-1")
                        .header("X-Correlation-Id", "create-season-request-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/seasons/" + SEASON_ID))
                .andExpect(jsonPath("$.code").value("SEASON-A"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<SeasonCommands.Create> command = ArgumentCaptor.forClass(SeasonCommands.Create.class);
        verify(seasonCommands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().code()).isEqualTo("SEASON-A");
        assertThat(command.getValue().plantedAreaHectares()).isEqualByComparingTo("10");
    }

    @Test
    void canonicalPatchVersionsShareFingerprintAndExplicitClearsRemainSemantic() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_MANAGE));
        when(seasonCommands.update(any(), any(), any()))
                .thenReturn(completed(200, season(3, Season.Status.PLANNED)));
        String body = "{\"clearBudgetVnd\":true,\"reasonCode\":\"season_change\"}";

        patchWithVersion("patch-season-1", "\"002\"", body);
        patchWithVersion("patch-season-2", "\"2\"", body);
        patchWithVersion("patch-season-3", "\"3\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<SeasonCommands.Update> commands = ArgumentCaptor.forClass(SeasonCommands.Update.class);
        verify(seasonCommands, org.mockito.Mockito.times(3))
                .update(requests.capture(), org.mockito.ArgumentMatchers.eq(SEASON_ID), commands.capture());
        var hashes = requests.getAllValues().stream().map(value -> value.fingerprint().commandHash()).toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(commands.getAllValues().getFirst().budgetVnd()).contains(java.util.Optional.empty());
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/seasons/{id}", SEASON_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""));
    }
}
