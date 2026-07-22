package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.CropHttpTestSupport.CROP_ID;
import static com.agriinsight.backend.farm.CropHttpTestSupport.completed;
import static com.agriinsight.backend.farm.CropHttpTestSupport.crop;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.CropCommandService;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class CropLifecycleHttpContractTest {

    private static final String BODY = "{\"reasonCode\":\"CROP_LIFECYCLE_CHANGE\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private CropCommandService cropCommands;

    @Test
    void lifecycleRequiresManagementIdempotencyAndStrongVersion() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        mockMvc.perform(post("/api/v1/crops/{id}/deactivate", CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cropCommands);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        mockMvc.perform(post("/api/v1/crops/{id}/deactivate", CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "forbidden-crop-lifecycle")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cropCommands);
    }

    @Test
    void deactivateAndReactivateReturnCurrentStrongEtags() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(cropCommands.deactivate(any(), any(), any()))
                .thenReturn(completed(200, crop(4, false)));
        when(cropCommands.reactivate(any(), any(), any()))
                .thenReturn(completed(200, crop(5, true)));

        mutate("deactivate", "deactivate-crop-1", "\"3\"", "\"4\"");
        mutate("reactivate", "reactivate-crop-1", "\"4\"", "\"5\"");
    }

    @Test
    void unknownLifecycleFieldsAreRejected() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        mockMvc.perform(post("/api/v1/crops/{id}/deactivate", CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-crop-lifecycle")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"CROP_CHANGE\",\"cascade\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        verifyNoInteractions(cropCommands);
    }

    private void mutate(String action, String key, String currentEtag, String responseEtag) throws Exception {
        mockMvc.perform(post("/api/v1/crops/{id}/" + action, CROP_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, currentEtag)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, responseEtag));
    }
}
