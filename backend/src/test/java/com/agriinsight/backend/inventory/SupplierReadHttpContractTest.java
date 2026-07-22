package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.SUPPLIER_ID;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.inventory.SupplierHttpTestSupport.supplier;
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
import com.agriinsight.backend.inventory.application.SupplierPage;
import com.agriinsight.backend.inventory.application.SupplierService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class SupplierReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private SupplierService suppliers;

    @Test
    void inventoryReadReturnsOnlySafeVersionedSupplierFields() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_READ));
        when(suppliers.list(any()))
                .thenReturn(new SupplierPage(List.of(supplier(4)), 25, 0, false));
        when(suppliers.get(SUPPLIER_ID)).thenReturn(supplier(4));

        mockMvc.perform(get("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25")
                        .param("active", "true")
                        .param("search", "supplier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SUPPLIER_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].bankAccount").doesNotExist())
                .andExpect(jsonPath("$.items[0].totalSpend").doesNotExist())
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/v1/suppliers/{id}", SUPPLIER_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("SUP-A"));
    }

    @Test
    void missingInventoryReadPermissionCannotEnumerateSuppliers() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());

        mockMvc.perform(get("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(suppliers);
    }

    @Test
    void paginationCapsRejectUnboundedQueriesBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_READ));

        mockMvc.perform(get("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        mockMvc.perform(get("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("offset", "10001"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(suppliers);
    }
}
