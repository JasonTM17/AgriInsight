package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.WAREHOUSE_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.completed;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.warehouse;
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
import com.agriinsight.backend.inventory.application.WarehouseCommandService;
import com.agriinsight.backend.inventory.application.WarehouseCommands;
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
class WarehouseMutationHttpContractTest {

    private static final String CREATE_BODY = """
            {"code":" wh-central ","displayName":" Central Warehouse ",
             "locationText":" Central Highlands "}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private WarehouseCommandService commands;

    @Test
    void invalidMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));

        mockMvc.perform(post("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/warehouses/{id}", WAREHOUSE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Warehouse\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/warehouses/{id}", WAREHOUSE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "set-and-clear")
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationText\":\"North\",\"clearLocationText\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WH-A\",\"displayName\":\"Warehouse\",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(commands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.create(any(), any())).thenReturn(completed(201, warehouse(0)));

        mockMvc.perform(post("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-warehouse-1")
                        .header("X-Correlation-Id", "create-warehouse-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(
                        HttpHeaders.LOCATION, "/api/v1/warehouses/" + WAREHOUSE_ID))
                .andExpect(jsonPath("$.code").value("WH-CENTRAL"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<WarehouseCommands.Create> command =
                ArgumentCaptor.forClass(WarehouseCommands.Create.class);
        verify(commands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().code()).isEqualTo("WH-CENTRAL");
        assertThat(command.getValue().locationText()).contains("Central Highlands");
    }

    @Test
    void canonicalPatchVersionsAndNullableLocationReachTheCommand() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.update(any(), any(), any())).thenReturn(completed(200, warehouse(8)));
        String body = "{\"clearLocationText\":true,\"reasonCode\":\"warehouse_change\"}";

        patchWithVersion("patch-warehouse-1", "\"007\"", body);
        patchWithVersion("patch-warehouse-2", "\"7\"", body);
        patchWithVersion("patch-warehouse-3", "\"8\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<WarehouseCommands.Update> capturedCommands =
                ArgumentCaptor.forClass(WarehouseCommands.Update.class);
        verify(commands, org.mockito.Mockito.times(3)).update(
                requests.capture(),
                org.mockito.ArgumentMatchers.eq(WAREHOUSE_ID),
                capturedCommands.capture());
        var hashes = requests.getAllValues().stream()
                .map(value -> value.fingerprint().commandHash())
                .toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(capturedCommands.getAllValues().getFirst().expectedVersion()).isEqualTo(7);
        assertThat(capturedCommands.getAllValues().getFirst().locationText())
                .contains(java.util.Optional.empty());
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/warehouses/{id}", WAREHOUSE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""));
    }
}
