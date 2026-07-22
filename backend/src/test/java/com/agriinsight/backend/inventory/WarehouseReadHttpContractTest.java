package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.WAREHOUSE_ID;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.inventory.WarehouseHttpTestSupport.warehouse;
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
import com.agriinsight.backend.inventory.application.WarehousePage;
import com.agriinsight.backend.inventory.application.WarehouseService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class WarehouseReadHttpContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private WarehouseService warehouses;

    @Test
    void inventoryReadReturnsBoundedPagesAndVersionedDetails() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_READ));
        when(warehouses.list(any()))
                .thenReturn(new WarehousePage(List.of(warehouse(4)), 25, 0, false));
        when(warehouses.get(WAREHOUSE_ID)).thenReturn(warehouse(4));

        mockMvc.perform(get("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25")
                        .param("active", "true")
                        .param("search", "central"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(WAREHOUSE_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].locationText").value("Central Highlands"))
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/v1/warehouses/{id}", WAREHOUSE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("WH-CENTRAL"));
    }

    @Test
    void missingInventoryReadPermissionCannotEnumerateWarehouses() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());

        mockMvc.perform(get("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(warehouses);
    }

    @Test
    void paginationCapsRejectUnboundedQueriesBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_READ));

        mockMvc.perform(get("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        mockMvc.perform(get("/api/v1/warehouses")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("offset", "10001"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(warehouses);
    }
}
