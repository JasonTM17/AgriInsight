package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.MATERIAL_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.SUPPLIER_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.TRANSACTION_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.WAREHOUSE_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.completed;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.receipt;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.reversal;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.stubIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommandService;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
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
class InventoryTransactionMutationHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private InventoryTransactionCommandService commands;

    @Test
    void invalidKindsAndClientSuppliedIssueFinanceFailBeforeService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));

        postTransaction("missing-finance", """
                {"kind":"RECEIPT","warehouseId":"%s","materialId":"%s",
                 "quantityBase":2.5,"occurredAt":"2027-01-01T08:00:00Z"}
                """.formatted(WAREHOUSE_ID, MATERIAL_ID), 400);
        postTransaction("issue-finance", """
                {"kind":"ISSUE","warehouseId":"%s","materialId":"%s",
                 "supplierId":"%s","quantityBase":1,"unitCostVnd":100,
                 "occurredAt":"2027-01-01T08:00:00Z","reason":"Use"}
                """.formatted(WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID), 400);
        mockMvc.perform(post("/api/v1/inventory/transactions/{id}/reversals", TRANSACTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-reversal")
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityBase\":1,\"reason\":\"Correction\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commands);
    }

    @Test
    void equivalentReceiptsShareFingerprintAndReturnSafeRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.post(any(), any())).thenReturn(completed(receipt()));
        String first = receiptBody(" receipt ", "2.5000", "100.00", " BATCH-A ");
        String second = receiptBody("RECEIPT", "2.5", "100", "BATCH-A");

        postTransaction("receipt-1", first, 201);
        postTransaction("receipt-2", second, 201);

        ArgumentCaptor<CommandExecutionRequest> requests =
                ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<InventoryTransactionCommands.Posting> posted =
                ArgumentCaptor.forClass(InventoryTransactionCommands.Posting.class);
        verify(commands, times(2)).post(requests.capture(), posted.capture());
        assertThat(requests.getAllValues().get(0).fingerprint().commandHash())
                .isEqualTo(requests.getAllValues().get(1).fingerprint().commandHash());
        assertThat(posted.getAllValues().getFirst())
                .isInstanceOf(InventoryTransactionCommands.Receipt.class);
    }

    @Test
    void reversalUsesStrongVersionAndContainsNoClientFinanceFields() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.reverse(any(), eq(TRANSACTION_ID), any()))
                .thenReturn(completed(reversal()));

        mockMvc.perform(post("/api/v1/inventory/transactions/{id}/reversals", TRANSACTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "reverse-1")
                        .header(HttpHeaders.IF_MATCH, "\"007\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityBase\":1,\"reason\":\" Correction \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.reversalOf").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<InventoryTransactionCommands.Reversal> command =
                ArgumentCaptor.forClass(InventoryTransactionCommands.Reversal.class);
        verify(commands).reverse(any(), eq(TRANSACTION_ID), command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(7);
        assertThat(command.getValue().reason()).isEqualTo("Correction");
    }

    private String receiptBody(String kind, String quantity, String cost, String batch) {
        return """
                {"kind":"%s","warehouseId":"%s","materialId":"%s",
                 "supplierId":"%s","quantityBase":%s,"unitCostVnd":%s,
                 "batchCode":"%s","expiryDate":"2027-12-31",
                 "occurredAt":"2027-01-01T08:00:00Z","referenceCode":"PO-1"}
                """.formatted(kind, WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID,
                quantity, cost, batch);
    }

    private void postTransaction(String key, String body, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/transactions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andExpect(expectedStatus == 201
                        ? header().string(HttpHeaders.LOCATION,
                                "/api/v1/inventory/transactions/" + TRANSACTION_ID)
                        : header().doesNotExist(HttpHeaders.LOCATION));
    }
}
