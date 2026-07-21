package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.COMMAND_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.completed;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.farm;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.FarmCommandService;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class FarmLifecycleHttpContractTest {

    private static final String BODY = "{\"reasonCode\":\"FARM_LIFECYCLE_CHANGE\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private FarmCommandService farmCommands;

    @Test
    void lifecycleRequiresIdempotencyAndStrongVersionHeaders() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        mockMvc.perform(post("/api/v1/farms/{id}/deactivate", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/farms/{id}/deactivate", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-lifecycle-field")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FARM_LIFECYCLE_CHANGE\",\"cascade\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(farmCommands);
    }

    @Test
    void deactivateAndReactivateReturnCurrentStrongEtags() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(farmCommands.deactivate(any(), any(), any())).thenReturn(completed(200, farm(4, false)));
        when(farmCommands.reactivate(any(), any(), any())).thenReturn(completed(200, farm(5, true)));

        mutate("deactivate", "deactivate-farm-1", "\"3\"", "\"4\"");
        mutate("reactivate", "reactivate-farm-1", "\"4\"", "\"5\"");
    }

    @Test
    void reusedLifecycleKeyWithChangedMeaningReturnsConflict() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(farmCommands.deactivate(any(), any(), any()))
                .thenReturn(new CommandExecutionResult.Conflict<>(COMMAND_ID));

        mockMvc.perform(post("/api/v1/farms/{id}/deactivate", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "reused-farm-key")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Request conflict"));
    }

    private void mutate(String action, String key, String currentEtag, String responseEtag) throws Exception {
        mockMvc.perform(post("/api/v1/farms/{id}/" + action, FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, currentEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, responseEtag));
    }
}
