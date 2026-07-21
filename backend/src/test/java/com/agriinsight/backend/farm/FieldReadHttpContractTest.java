package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.FIELD_ID;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.FieldPage;
import com.agriinsight.backend.farm.application.FieldService;
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
class FieldReadHttpContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private FieldService fields;

    @Test
    void farmReadReturnsBoundedPagesAndVersionedDetails() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        when(fields.list(any())).thenReturn(new FieldPage(List.of(field(4)), 25, 0, false));
        when(fields.get(FIELD_ID)).thenReturn(field(4));

        mockMvc.perform(get("/api/v1/fields")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("farmId", FARM_ID.toString()).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(FIELD_ID.toString()))
                .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.items[0].latitude").value(10.1234))
                .andExpect(jsonPath("$.limit").value(25));
        mockMvc.perform(get("/api/v1/fields/{id}", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("FIELD-A"));
    }

    @Test
    void permissionAndPaginationFailuresStopBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of());
        mockMvc.perform(get("/api/v1/fields")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fields);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        mockMvc.perform(get("/api/v1/fields")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fields);
    }
}
