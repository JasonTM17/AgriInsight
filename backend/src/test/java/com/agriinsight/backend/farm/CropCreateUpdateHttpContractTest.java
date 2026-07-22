package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.CropHttpTestSupport.CROP_ID;
import static com.agriinsight.backend.farm.CropHttpTestSupport.completed;
import static com.agriinsight.backend.farm.CropHttpTestSupport.crop;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
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
import com.agriinsight.backend.farm.api.CropUpdateRequest;
import com.agriinsight.backend.farm.application.CropCommandService;
import com.agriinsight.backend.farm.application.CropCommands;
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
class CropCreateUpdateHttpContractTest {

    private static final String CREATE_BODY = """
            {"code":" coffee-a ","displayName":" Arabica Coffee ",
             "scientificName":" Coffea arabica ","reasonCode":"crop_create"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private CropCommandService cropCommands;

    @Test
    void omittedClearFlagDeserializesWithoutRelaxingStrictPrimitiveHandling() throws Exception {
        CropUpdateRequest request = objectMapper.readValue(
                "{\"displayName\":\"Updated Crop\"}", CropUpdateRequest.class);

        assertThat(request.clearScientificName()).isFalse();
    }

    @Test
    void invalidMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        mockMvc.perform(post("/api/v1/crops")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/crops/{id}", CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-crop-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Crop\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/crops")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-crop-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"RICE-A\",\"displayName\":\"Rice\","
                                + "\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(cropCommands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(cropCommands.create(any(), any())).thenReturn(completed(201, crop(0)));

        mockMvc.perform(post("/api/v1/crops")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-crop-1")
                        .header("X-Correlation-Id", "create-crop-request-1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/crops/" + CROP_ID))
                .andExpect(jsonPath("$.code").value("COFFEE-A"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<CropCommands.Create> command = ArgumentCaptor.forClass(CropCommands.Create.class);
        verify(cropCommands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().code()).isEqualTo("COFFEE-A");
        assertThat(command.getValue().scientificName()).contains("Coffea arabica");
    }

    @Test
    void canonicalPatchVersionsShareFingerprintAndExplicitClearRemainsSemantic() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(cropCommands.update(any(), any(), any())).thenReturn(completed(200, crop(3)));
        String body = "{\"clearScientificName\":true,\"reasonCode\":\"crop_change\"}";

        patchWithVersion("patch-crop-1", "\"002\"", body);
        patchWithVersion("patch-crop-2", "\"2\"", body);
        patchWithVersion("patch-crop-3", "\"3\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<CropCommands.Update> commands = ArgumentCaptor.forClass(CropCommands.Update.class);
        verify(cropCommands, org.mockito.Mockito.times(3))
                .update(requests.capture(), org.mockito.ArgumentMatchers.eq(CROP_ID), commands.capture());
        var hashes = requests.getAllValues().stream().map(value -> value.fingerprint().commandHash()).toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(commands.getAllValues().getFirst().scientificName()).contains(java.util.Optional.empty());
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/crops/{id}", CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""));
    }
}
