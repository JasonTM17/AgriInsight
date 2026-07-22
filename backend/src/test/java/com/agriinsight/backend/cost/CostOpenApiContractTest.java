package com.agriinsight.backend.cost;

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
class CostOpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private IdentityBootstrapPort bootstrapPort;

    @Test
    void documentsOperatingCostCommandsAndTheSeparateSummaryLens() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/cost-entries'].post.summary")
                        .value("Post an operating cost entry"))
                .andExpect(jsonPath("$.paths['/api/v1/cost-entries/{id}/corrections']"
                        + ".post.summary").value("Correct an operating cost entry"))
                .andExpect(jsonPath("$.paths['/api/v1/cost-summaries'].get.summary")
                        .value("Summarize the operating cost ledger"))
                .andExpect(jsonPath("$.components.schemas.OperatingCostPostRequest"
                        + ".properties.category.example").value("LABOR"))
                .andExpect(jsonPath("$.components.schemas.OperatingCostPostRequest"
                        + ".properties.amountVnd.example").value(1250000.0));
    }
}
