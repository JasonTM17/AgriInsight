package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.FIELD_ID;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.completed;
import static com.agriinsight.backend.farm.FieldHttpTestSupport.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.FieldCommandService;
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
class FieldLifecycleHttpContractTest {

    private static final String BODY = "{\"reasonCode\":\"FIELD_LIFECYCLE_CHANGE\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @Autowired private TenantPrincipalLoader principalLoader;
    @Autowired private FieldCommandService fieldCommands;

    @Test
    void lifecycleRequiresManagementIdempotencyAndStrongVersion() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        mockMvc.perform(post("/api/v1/fields/{id}/deactivate", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fieldCommands);

        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_READ));
        mockMvc.perform(post("/api/v1/fields/{id}/deactivate", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "forbidden-field-lifecycle")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fieldCommands);
    }

    @Test
    void deactivateAndReactivateReturnCurrentStrongEtags() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(fieldCommands.deactivate(any(), any(), any()))
                .thenReturn(completed(200, field(4, false)));
        when(fieldCommands.reactivate(any(), any(), any()))
                .thenReturn(completed(200, field(5, true)));

        mutate("deactivate", "deactivate-field-1", "\"3\"", "\"4\"");
        mutate("reactivate", "reactivate-field-1", "\"4\"", "\"5\"");
    }

    @Test
    void unknownLifecycleFieldsAreRejected() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        mockMvc.perform(post("/api/v1/fields/{id}/deactivate", FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-field-lifecycle")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FIELD_CHANGE\",\"cascade\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        verifyNoInteractions(fieldCommands);
    }

    private void mutate(String action, String key, String currentEtag, String responseEtag) throws Exception {
        mockMvc.perform(post("/api/v1/fields/{id}/" + action, FIELD_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, currentEtag)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, responseEtag));
    }
}
