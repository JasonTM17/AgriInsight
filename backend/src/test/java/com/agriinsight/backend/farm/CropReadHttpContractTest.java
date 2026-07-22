package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.CropHttpTestSupport.CROP_ID;
import static com.agriinsight.backend.farm.CropHttpTestSupport.crop;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.CropPage;
import com.agriinsight.backend.farm.application.CropService;
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
class CropReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private CropService crops;

    @Test
    void farmReadReturnsBoundedCatalogPagesAndVersionedDetails() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        when(crops.list(any())).thenReturn(new CropPage(List.of(crop(4)), 25, 0, false));
        when(crops.get(CROP_ID)).thenReturn(crop(4));

        mockMvc.perform(get("/api/v1/crops")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("active", "true").param("search", "arabica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(CROP_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].scientificName").value("Coffea arabica"))
                .andExpect(jsonPath("$.limit").value(25));
        mockMvc.perform(get("/api/v1/crops/{id}", CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("COFFEE-A"));
    }

    @Test
    void permissionAndPaginationFailuresStopBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());
        mockMvc.perform(get("/api/v1/crops")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(crops);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        mockMvc.perform(get("/api/v1/crops")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("offset", "10001"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(crops);
    }
}
