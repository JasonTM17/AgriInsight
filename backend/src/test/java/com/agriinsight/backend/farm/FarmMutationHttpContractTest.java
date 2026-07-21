package com.agriinsight.backend.farm;

import static com.agriinsight.backend.farm.FarmHttpTestSupport.AUTHORIZATION;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.ACTOR_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.FARM_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.TENANT_ID;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.completed;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.farm;
import static com.agriinsight.backend.farm.FarmHttpTestSupport.stubIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.farm.application.FarmCommandService;
import com.agriinsight.backend.farm.application.FarmCommands;
import com.agriinsight.backend.identity.IdentitySecurityContext;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class FarmMutationHttpContractTest {

    private static final String CREATE_BODY =
            "{\"code\":\" north \" ,\"displayName\":\" North Farm \"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private FarmCommandService farmCommands;

    @Test
    void invalidMutationContractsFailBeforeTheService() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));

        mockMvc.perform(post("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/farms/{id}", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "weak-patch")
                        .header(HttpHeaders.IF_MATCH, "W/\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Farm\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NORTH\",\"displayName\":\"North Farm\",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        mockMvc.perform(patch("/api/v1/farms/{id}", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "unknown-patch-field")
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"North Farm\",\"tenantId\":\"hidden\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(farmCommands);
    }

    @Test
    void createReturnsLocationEtagAndCanonicalRepresentation() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(farmCommands.create(any(), any())).thenReturn(completed(201, farm(0)));

        mockMvc.perform(post("/api/v1/farms")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-farm-1")
                        .header("X-Correlation-Id", "create-farm-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/farms/" + FARM_ID))
                .andExpect(jsonPath("$.code").value("NORTH"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());

        ArgumentCaptor<CommandExecutionRequest> request = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<FarmCommands.Create> command = ArgumentCaptor.forClass(FarmCommands.Create.class);
        verify(farmCommands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(command.getValue().code()).isEqualTo("NORTH");
        assertThat(command.getValue().displayName()).isEqualTo("North Farm");
    }

    @Test
    void canonicalPatchVersionsShareFingerprintWhileChangedVersionsDoNot() throws Exception {
        stubIdentity(jwtDecoder, principalLoader, Set.of(Permission.FARM_MANAGE));
        when(farmCommands.update(any(), any(), any())).thenReturn(completed(200, farm(8)));
        String body = "{\"code\":\" north \",\"reasonCode\":\"master_data_change\"}";

        patchWithVersion("patch-farm-1", "\"007\"", body);
        patchWithVersion("patch-farm-2", "\"7\"", body);
        patchWithVersion("patch-farm-3", "\"8\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<FarmCommands.Update> commands = ArgumentCaptor.forClass(FarmCommands.Update.class);
        verify(farmCommands, org.mockito.Mockito.times(3))
                .update(requests.capture(), org.mockito.ArgumentMatchers.eq(FARM_ID), commands.capture());
        var hashes = requests.getAllValues().stream().map(value -> value.fingerprint().commandHash()).toList();
        assertThat(hashes.get(0)).isEqualTo(hashes.get(1));
        assertThat(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(commands.getAllValues().getFirst().expectedVersion()).isEqualTo(7);
        assertThat(commands.getAllValues().getFirst().code()).contains("NORTH");
    }

    private void patchWithVersion(String key, String version, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/farms/{id}", FARM_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""));
    }
}
