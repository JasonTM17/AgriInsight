package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.TenantUserCommandService;
import com.agriinsight.backend.identity.application.TenantUserCommands;
import com.agriinsight.backend.identity.application.TenantUserProfile;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.IfMatchVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/users")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantUserMutationController {

    private static final String USERS = ApiVersion.PREFIX + "/users";
    private static final String DEACTIVATE = USERS + "/{id}/deactivate";
    private static final String REACTIVATE = USERS + "/{id}/reactivate";

    private final TenantUserCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public TenantUserMutationController(
            TenantUserCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<TenantUserResponse> create(
            @Valid @RequestBody TenantUserCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        TenantAuditMetadata audit = audit(body.reasonCode(), correlationId);
        var command = new TenantUserCommands.Create(
                body.displayName(),
                Optional.ofNullable(body.email()),
                body.issuer(),
                body.subject(),
                audit);
        var execution = commands.create(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        USERS,
                        Map.of(),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "displayName", body.displayName(),
                                "email", Optional.ofNullable(body.email()),
                                "issuer", body.issuer(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()),
                                "subject", body.subject())),
                        Map.of(),
                        correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        TenantUserResponse response = TenantUserResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(USERS + "/" + response.id()))
                .body(response);
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<TenantUserResponse> deactivate(
            @PathVariable("id") UUID profileId,
            @Valid @RequestBody TenantUserLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(profileId, body, idempotencyKey, ifMatch, principal, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<TenantUserResponse> reactivate(
            @PathVariable("id") UUID profileId,
            @Valid @RequestBody TenantUserLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(profileId, body, idempotencyKey, ifMatch, principal, request, true);
    }

    private ResponseEntity<TenantUserResponse> changeActive(
            UUID profileId,
            TenantUserLifecycleRequest body,
            String idempotencyKey,
            String ifMatch,
            TenantPrincipal principal,
            HttpServletRequest request,
            boolean active) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        TenantAuditMetadata audit = audit(body.reasonCode(), correlationId);
        TenantUserCommands.Lifecycle command = new TenantUserCommands.Lifecycle(expected.value(), audit);
        String route = active ? REACTIVATE : DEACTIVATE;
        var execution = active
                ? commands.reactivate(
                        fingerprints.create(
                                principal,
                                idempotencyKey,
                                "POST",
                                route,
                                Map.of("id", profileId.toString()),
                                Map.of(),
                                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                                correlationId),
                        profileId,
                        command)
                : commands.deactivate(
                        fingerprints.create(
                                principal,
                                idempotencyKey,
                                "POST",
                                route,
                                Map.of("id", profileId.toString()),
                                Map.of(),
                                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                                correlationId),
                        profileId,
                        command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        TenantUserResponse response = TenantUserResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(
                completed,
                response,
                response.version());
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
