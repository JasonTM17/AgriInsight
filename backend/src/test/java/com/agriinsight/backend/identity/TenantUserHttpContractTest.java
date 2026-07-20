package com.agriinsight.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.identity.application.TenantUserCommandService;
import com.agriinsight.backend.identity.application.TenantUserCommands;
import com.agriinsight.backend.identity.application.TenantUserProfile;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IdentitySecurityContext
class TenantUserHttpContractTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID COMMAND_ID = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final String AUTHORIZATION = "Bearer tenant-user-admin-token";
    private static final String CREATE_BODY = """
            {"displayName":"Mai Tran","email":"mai@example.test",
             "issuer":"https://identity.example.test/issuer","subject":"provider-user-2"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TenantPrincipalLoader principalLoader;

    @Autowired
    private TenantUserCommandService userCommands;

    @Test
    void mutationHeadersAreMandatoryAndWeakEtagsAreRejectedBeforeTheService() throws Exception {
        stubIdentity();

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        String lifecycleBody = "{\"reasonCode\":\"ACCESS_REVOKED\"}";
        mockMvc.perform(post("/api/v1/users/{id}/deactivate", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "deactivate-without-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycleBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/users/{id}/deactivate", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "deactivate-weak-version")
                        .header(HttpHeaders.IF_MATCH, "W/\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycleBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userCommands);
    }

    @Test
    void createReturnsLocationEtagAndThePersistedRepresentation() throws Exception {
        stubIdentity();
        TenantUserProfile profile = profile(0);
        when(userCommands.create(any(), any())).thenReturn(completed(201, profile));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "create-user-command-1")
                        .header("X-Correlation-Id", "create-user-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/users/" + PROFILE_ID))
                .andExpect(jsonPath("$.id").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.email").value("mai@example.test"));

        ArgumentCaptor<CommandExecutionRequest> request = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        ArgumentCaptor<TenantUserCommands.Create> command = ArgumentCaptor.forClass(TenantUserCommands.Create.class);
        verify(userCommands).create(request.capture(), command.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().principalId()).isEqualTo(ACTOR_ID);
        assertThat(request.getValue().correlationId()).contains("create-user-request-1");
        assertThat(command.getValue().displayName()).isEqualTo("Mai Tran");
    }

    @Test
    void canonicalIfMatchValuesShareAFingerprintWhileChangedVersionsConflict() throws Exception {
        stubIdentity();
        when(userCommands.deactivate(any(), any(), any())).thenReturn(completed(200, profile(8)));
        String body = "{\"reasonCode\":\"ACCESS_REVOKED\"}";

        mutateWithIfMatch("fingerprint-command-1", "\"007\"", body);
        mutateWithIfMatch("fingerprint-command-2", "\"7\"", body);
        mutateWithIfMatch("fingerprint-command-3", "\"8\"", body);

        ArgumentCaptor<CommandExecutionRequest> requests = ArgumentCaptor.forClass(CommandExecutionRequest.class);
        verify(userCommands, org.mockito.Mockito.times(3)).deactivate(requests.capture(), any(), any());
        var fingerprints = requests.getAllValues().stream().map(CommandExecutionRequest::fingerprint).toList();
        assertThat(fingerprints.get(0).commandHash()).isEqualTo(fingerprints.get(1).commandHash());
        assertThat(fingerprints.get(2).commandHash()).isNotEqualTo(fingerprints.get(1).commandHash());
    }

    @Test
    void changedMeaningForAnExistingIdempotencyKeyReturnsConflict() throws Exception {
        stubIdentity();
        when(userCommands.deactivate(any(), any(), any()))
                .thenReturn(new CommandExecutionResult.Conflict<>(COMMAND_ID));

        mockMvc.perform(post("/api/v1/users/{id}/deactivate", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", "reused-command-key")
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"ACCESS_REVOKED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Request conflict"));
    }

    private void mutateWithIfMatch(String key, String ifMatch, String body) throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/deactivate", PROFILE_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.IF_MATCH, ifMatch)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void stubIdentity() {
        when(jwtDecoder.decode("tenant-user-admin-token")).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID, TENANT_ID, "TENANT-A", Optional.of("Admin"), Optional.empty(), Optional.of("mfa"),
                Set.of(Role.TENANT_ADMIN), Set.of(Permission.IDENTITY_USER_MANAGE)));
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("tenant-user-admin-token")
                .header("alg", "RS256").issuer("https://identity.example.test/issuer")
                .subject("provider-admin").audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30)).expiresAt(now.plusSeconds(90))
                .claim("token_use", "access").build();
    }

    private TenantUserProfile profile(long version) {
        return new TenantUserProfile(
                PROFILE_ID, TENANT_ID, "Mai Tran", Optional.of("mai@example.test"), true, version);
    }

    private CommandExecutionResult.Completed<TenantUserProfile> completed(
            int status,
            TenantUserProfile profile) {
        return new CommandExecutionResult.Completed<>(COMMAND_ID, false, status,
                new CommandTarget("USER_PROFILE", profile.id(), profile.version()), Optional.of(profile));
    }
}
