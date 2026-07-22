package com.agriinsight.backend.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
@ActiveProfiles({"test", "dev"})
class InventoryOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityBootstrapPort bootstrapPort;

    @Test
    void documentsInventoryCommandsAndBaseUnitExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/inventory/transactions'].post.summary")
                        .value("Post an inventory receipt or issue"))
                .andExpect(jsonPath("$.paths['/api/v1/inventory/transactions/{id}/reversals']"
                        + ".post.summary").value("Reverse an inventory transaction"))
                .andExpect(jsonPath("$.components.schemas.InventoryTransactionPostRequest"
                        + ".properties.kind.example").value("RECEIPT"))
                .andExpect(jsonPath("$.components.schemas.InventoryTransactionPostRequest"
                        + ".properties.quantityBase.example").value(250.0))
                .andExpect(jsonPath("$.components.schemas.InventoryReversalRequest"
                        + ".properties.reasonCode.example").value("DUPLICATE_POSTING"));
    }
}
