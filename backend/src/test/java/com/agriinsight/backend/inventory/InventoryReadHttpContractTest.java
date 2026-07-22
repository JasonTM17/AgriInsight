package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.MATERIAL_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.TRANSACTION_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.WAREHOUSE_ID;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.balance;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.lot;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.receipt;
import static com.agriinsight.backend.inventory.InventoryHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.InventoryReadService;
import com.agriinsight.backend.inventory.application.InventoryTransactionPage;
import com.agriinsight.backend.inventory.application.StockBalancePage;
import com.agriinsight.backend.inventory.application.StockLotPage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class InventoryReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private InventoryReadService inventory;

    @Test
    void boundedReadRoutesReturnSafeInventoryRepresentations() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_READ));
        when(inventory.listBalances(any()))
                .thenReturn(new StockBalancePage(List.of(balance()), 25, 0, false));
        when(inventory.listLots(any()))
                .thenReturn(new StockLotPage(List.of(lot()), 25, 0, false));
        when(inventory.listTransactions(any()))
                .thenReturn(new InventoryTransactionPage(List.of(receipt()), 25, 0, false));
        when(inventory.getTransaction(TRANSACTION_ID)).thenReturn(receipt());

        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .queryParam("limit", "25")
                        .queryParam("warehouseId", WAREHOUSE_ID.toString())
                        .queryParam("lowStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].warehouseCode").value("WH-A"))
                .andExpect(jsonPath("$.items[0].lowStock").value(true))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
        mockMvc.perform(get("/api/v1/inventory/lots")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .queryParam("limit", "25")
                        .queryParam("materialId", MATERIAL_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].batchCode").value("BATCH-A"))
                .andExpect(jsonPath("$.items[0].supplierCode").value("SUP-A"));
        mockMvc.perform(get("/api/v1/inventory/transactions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .queryParam("limit", "25")
                        .queryParam("kind", "RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("RECEIPT"))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
        mockMvc.perform(get("/api/v1/inventory/transactions/{id}", TRANSACTION_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()));
    }

    @Test
    void invalidRangeAndMissingReadPermissionFailBeforeService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_READ));
        mockMvc.perform(get("/api/v1/inventory/transactions")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .queryParam("occurredFrom", "2027-02-01T00:00:00Z")
                        .queryParam("occurredTo", "2027-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inventory);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }
}
