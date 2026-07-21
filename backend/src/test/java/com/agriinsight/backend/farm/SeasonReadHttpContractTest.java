package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.SEASON_ID;
import static com.agriinsight.backend.farm.SeasonHttpTestSupport.season;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.SeasonPage;
import com.agriinsight.backend.farm.application.SeasonService;
import com.agriinsight.backend.farm.domain.Season;
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
class SeasonReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private SeasonService seasons;

    @Test
    void seasonReadPermissionReturnsScopedPagesAndVersionedDetails() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_READ));
        when(seasons.list(any())).thenReturn(new SeasonPage(
                List.of(season(4, Season.Status.ACTIVE)), 25, 0, false));
        when(seasons.get(SEASON_ID)).thenReturn(season(4, Season.Status.ACTIVE));

        mockMvc.perform(get("/api/v1/seasons")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "25")
                        .param("status", "ACTIVE")
                        .param("search", "arabica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SEASON_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.limit").value(25));
        mockMvc.perform(get("/api/v1/seasons/{id}", SEASON_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("SEASON-A"));
    }

    @Test
    void missingPermissionAndUnboundedPaginationFailBeforeService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());
        mockMvc.perform(get("/api/v1/seasons").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(seasons);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.SEASON_READ));
        mockMvc.perform(get("/api/v1/seasons")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verifyNoInteractions(seasons);
    }
}
