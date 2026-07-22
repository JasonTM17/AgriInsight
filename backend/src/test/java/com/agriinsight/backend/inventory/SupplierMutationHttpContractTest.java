package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.SUPPLIER_ID;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.completed;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.supplier;
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
import com.agriinsight.backend.inventory.application.SupplierCommandService;
import com.agriinsight.backend.inventory.application.SupplierCommands;
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
class SupplierMutationHttpContractTest {

    private static final String CREATE_BODY = """
            {"code":" sup-a ","displayName":" Supplier A "}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private SupplierCommandService commands;

    @Test
    void invalidAndFinanceBearingMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));

        mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/suppliers/{id}", SUPPLIER_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Supplier\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/suppliers/{id}", SUPPLIER_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "empty-patch")
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "finance-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SUP-A\",\"displayName\":\"Supplier A\","
                                + "\"bankAccount\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(commands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalSafeRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.create(any(), any())).thenReturn(completed(201, supplier(0)));

        mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-supplier-1")
                        .header("X-Correlation-Id", "create-supplier-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(
                        HttpHeaders.LOCATION, "/api/v1/suppliers/" + SUPPLIER_ID))
                .andExpect(jsonPath("$.code").value("SUP-A"))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.bankAccount").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<SupplierCommands.Create> command =
                ArgumentCaptor.forClass(SupplierCommands.Create.class);
        verify(commands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().code()).isEqualTo("SUP-A");
    }

    @Test
    void canonicalPatchVersionsReachTheCommand() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.update(any(), any(), any())).thenReturn(completed(200, supplier(8)));
        String body = "{\"displayName\":\"Updated Supplier\","
                + "\"reasonCode\":\"supplier_change\"}";

        patchWithVersion("patch-supplier-1", "\"007\"", body);
        patchWithVersion("patch-supplier-2", "\"7\"", body);
        patchWithVersion("patch-supplier-3", "\"8\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<SupplierCommands.Update> capturedCommands =
                ArgumentCaptor.forClass(SupplierCommands.Update.class);
        verify(commands, org.mockito.Mockito.times(3)).update(
                requests.capture(),
                org.mockito.ArgumentMatchers.eq(SUPPLIER_ID),
                capturedCommands.capture());
        var hashes = requests.getAllValues().stream()
                .map(value -> value.fingerprint().commandHash())
                .toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(capturedCommands.getAllValues().getFirst().expectedVersion()).isEqualTo(7);
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/suppliers/{id}", SUPPLIER_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""));
    }
}
