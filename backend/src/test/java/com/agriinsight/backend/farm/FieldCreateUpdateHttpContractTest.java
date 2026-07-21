package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.FIELD_ID;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.completed;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.field;
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
import com.agriinsight.backend.farm.api.FieldUpdateRequest;
import com.agriinsight.backend.farm.application.FieldCommandService;
import com.agriinsight.backend.farm.application.FieldCommands;
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
class FieldCreateUpdateHttpContractTest {

    private static final String CREATE_BODY = """
            {"code":" field-a ","displayName":" North Field ","areaHectares":12.5000,
             "responsibleEmployeeId":"33000000-0000-0000-0000-000000000001",
             "latitude":10.123400,"longitude":106.765400,
             "soilType":" Loam ","irrigationType":" Drip "}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private FieldCommandService fieldCommands;

    @Test
    void omittedClearFlagsDeserializeWithoutRelaxingStrictPrimitiveHandling() throws Exception {
        FieldUpdateRequest request = objectMapper.readValue(
                "{\"displayName\":\"Updated Field\"}", FieldUpdateRequest.class);

        assertThat(request.clearCoordinates()).isFalse();
        assertThat(request.clearResponsibleEmployeeId()).isFalse();
    }

    @Test
    void invalidMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        mockMvc.perform(post("/api/v1/farms/{farmId}/fields", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/farms/{farmId}/fields", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "partial-coordinates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FIELD-B\",\"displayName\":\"Field B\","
                                + "\"areaHectares\":1,\"latitude\":10}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/fields/{id}", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-field-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Field\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/fields/{id}", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-field-patch")
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Field\",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(fieldCommands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(fieldCommands.create(any(), any())).thenReturn(completed(201, field(0)));

        mockMvc.perform(post("/api/v1/farms/{farmId}/fields", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-field-1")
                        .header("X-Correlation-Id", "create-field-request-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/fields/" + FIELD_ID))
                .andExpect(jsonPath("$.code").value("FIELD-A"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<FieldCommands.Create> command = ArgumentCaptor.forClass(FieldCommands.Create.class);
        verify(fieldCommands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().farmId()).isEqualTo(FARM_ID);
        assertThat(command.getValue().areaHectares()).isEqualByComparingTo("12.5");
    }

    @Test
    void canonicalPatchVersionsShareFingerprintAndExplicitClearsRemainSemantic() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(fieldCommands.update(any(), any(), any())).thenReturn(completed(200, field(3)));
        String body = "{\"clearCoordinates\":true,\"clearSoilType\":true,"
                + "\"reasonCode\":\"field_change\"}";

        patchWithVersion("patch-field-1", "\"002\"", body);
        patchWithVersion("patch-field-2", "\"2\"", body);
        patchWithVersion("patch-field-3", "\"3\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<FieldCommands.Update> commands = ArgumentCaptor.forClass(FieldCommands.Update.class);
        verify(fieldCommands, org.mockito.Mockito.times(3))
                .update(requests.capture(), org.mockito.ArgumentMatchers.eq(FIELD_ID), commands.capture());
        var hashes = requests.getAllValues().stream().map(value -> value.fingerprint().commandHash()).toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(commands.getAllValues().getFirst().coordinates()).contains(java.util.Optional.empty());
        assertThat(commands.getAllValues().getFirst().soilType()).contains(java.util.Optional.empty());
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/fields/{id}", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""));
    }
}
