package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.MATERIAL_ID;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.completed;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.material;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.stubIdentity;
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
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.MaterialCommandService;
import com.agriinsight.backend.inventory.application.MaterialCommands;
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

@IdentitySecurityContext
class MaterialMutationHttpContractTest {

    private static final String CREATE_BODY = """
            {"code":" fert-a ","displayName":" Fertilizer ",
             "baseUnit":" kg ","minimumStockQuantity":12.5000}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private MaterialCommandService commands;

    @Test
    void invalidMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));

        mockMvc.perform(post("/api/v1/materials")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/materials/{id}", MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Fertilizer\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/materials/{id}", MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "set-and-clear")
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumStockQuantity\":5,\"clearMinimumStockQuantity\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/materials")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FERT-A\",\"displayName\":\"Fertilizer\","
                                + "\"baseUnit\":\"KG\",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(commands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.create(any(), any())).thenReturn(completed(201, material(0)));

        mockMvc.perform(post("/api/v1/materials")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-material-1")
                        .header("X-Correlation-Id", "create-material-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(
                        HttpHeaders.LOCATION, "/api/v1/materials/" + MATERIAL_ID))
                .andExpect(jsonPath("$.code").value("FERT-A"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<MaterialCommands.Create> command =
                ArgumentCaptor.forClass(MaterialCommands.Create.class);
        verify(commands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().code()).isEqualTo("FERT-A");
        assertThat(command.getValue().minimumStockQuantity()).contains(new java.math.BigDecimal("12.5"));
    }

    @Test
    void equivalentCreateRepresentationsShareTheCanonicalFingerprint() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.create(any(), any())).thenReturn(completed(201, material(0)));

        createWithBody("canonical-material-1", CREATE_BODY);
        createWithBody(
                "canonical-material-2",
                "{\"code\":\"FERT-A\",\"displayName\":\"Fertilizer\","
                        + "\"baseUnit\":\"KG\",\"minimumStockQuantity\":12.5}");

        ArgumentCaptor<CommandExecutionRequest> requests =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        verify(commands, org.mockito.Mockito.times(2)).create(requests.capture(), any());
        assertThat(requests.getAllValues().get(0).fingerprint().commandHash())
                .isEqualTo(requests.getAllValues().get(1).fingerprint().commandHash());
    }

    @Test
    void canonicalPatchVersionsAndNullableMinimumReachTheCommand() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.update(any(), any(), any())).thenReturn(completed(200, material(8)));
        String body = "{\"clearMinimumStockQuantity\":true,"
                + "\"reasonCode\":\"material_change\"}";

        patchWithVersion("patch-material-1", "\"007\"", body);
        patchWithVersion("patch-material-2", "\"7\"", body);
        patchWithVersion("patch-material-3", "\"8\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<MaterialCommands.Update> capturedCommands =
                ArgumentCaptor.forClass(MaterialCommands.Update.class);
        verify(commands, org.mockito.Mockito.times(3)).update(
                requests.capture(),
                org.mockito.ArgumentMatchers.eq(MATERIAL_ID),
                capturedCommands.capture());
        var hashes = requests.getAllValues().stream()
                .map(value -> value.fingerprint().commandHash())
                .toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(capturedCommands.getAllValues().getFirst().expectedVersion()).isEqualTo(7);
        assertThat(capturedCommands.getAllValues().getFirst().minimumStockQuantity())
                .contains(java.util.Optional.empty());
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/materials/{id}", MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""));
    }

    private void createWithBody(String key, String body) throws Exception {
        mockMvc.perform(post("/api/v1/materials")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
