package com.agriinsight.backend.inventory;

import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.COMMAND_ID;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.MATERIAL_ID;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.completed;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.material;
import static com.agriinsight.backend.inventory.MaterialHttpTestSupport.stubIdentity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.MaterialCommandService;
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
class MaterialLifecycleHttpContractTest {

    private static final String BODY = "{\"reasonCode\":\"MATERIAL_LIFECYCLE_CHANGE\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private MaterialCommandService commands;

    @Test
    void lifecycleRequiresIdempotencyAndStrongVersionHeaders() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));

        mockMvc.perform(post("/api/v1/materials/{id}/deactivate", MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/materials/{id}/deactivate", MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-lifecycle-field")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"MATERIAL_LIFECYCLE_CHANGE\",\"cascade\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(commands);
    }

    @Test
    void deactivateAndReactivateReturnCurrentStrongEtags() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.deactivate(any(), any(), any()))
                .thenReturn(completed(200, material(4, false)));
        when(commands.reactivate(any(), any(), any()))
                .thenReturn(completed(200, material(5, true)));

        mutate("deactivate", "deactivate-material-1", "\"3\"", "\"4\"");
        mutate("reactivate", "reactivate-material-1", "\"4\"", "\"5\"");
    }

    @Test
    void reusedLifecycleKeyWithChangedMeaningReturnsConflict() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.INVENTORY_MANAGE));
        when(commands.deactivate(any(), any(), any()))
                .thenReturn(new CommandExecutionResult.Conflict<>(COMMAND_ID));

        mockMvc.perform(post("/api/v1/materials/{id}/deactivate", MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "reused-material-key")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Request conflict"));
    }

    private void mutate(
            String action,
            String key,
            String currentEtag,
            String responseEtag) throws Exception {
        mockMvc.perform(post("/api/v1/materials/{id}/" + action, MATERIAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, currentEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, responseEtag));
    }
}
