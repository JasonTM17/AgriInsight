package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.farm;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.FarmPage;
import com.agriinsight.backend.farm.application.FarmService;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class FarmReadHttpContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private FarmService farms;

    @Test
    void farmReadPermissionReturnsBoundedPagesAndVersionedDetails() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        when(farms.list(any())).thenReturn(new FarmPage(List.of(farm(4)), 25, 0, false));
        when(farms.get(FARM_ID)).thenReturn(farm(4));

        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25")
                        .param("active", "true")
                        .param("search", "north"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(FARM_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/v1/farms/{id}", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("NORTH"));
    }

    @Test
    void identityWithoutFarmReadPermissionCannotEnumerateFarms() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());

        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(farms);
    }

    @Test
    void paginationCapsRejectUnboundedQueriesBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));

        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("offset", "10001"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(farms);
    }
}
