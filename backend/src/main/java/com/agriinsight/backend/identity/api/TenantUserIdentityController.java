package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.identity.application.TenantUserCommandService;
import com.agriinsight.backend.identity.application.TenantUserCommands;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/users/{id}/external-identities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantUserIdentityController {

    private static final String LINK_ROUTE = ApiVersion.PREFIX + "/users/{id}/external-identities";
    private static final String UNLINK_ROUTE = LINK_ROUTE + "/{identityId}/unlink";

    private final TenantUserCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public TenantUserIdentityController(
            TenantUserCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<ExternalIdentityResponse> link(
            @PathVariable("id") UUID profileId,
            @Valid @RequestBody TenantExternalIdentityRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        TenantAuditMetadata audit = audit(body.reasonCode(), correlationId);
        TenantUserCommands.LinkIdentity command = new TenantUserCommands.LinkIdentity(
                body.issuer(),
                body.subject(),
                audit);
        var execution = commands.linkIdentity(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        LINK_ROUTE,
                        Map.of("id", profileId.toString()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "issuer", body.issuer(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()),
                                "subject", body.subject())),
                        Map.of(),
                        correlationId),
                profileId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        ExternalIdentityResponse response = ExternalIdentityResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .body(response);
    }

    @PostMapping("/{identityId}/unlink")
    ResponseEntity<CommandReceiptResponse> unlink(
            @PathVariable("id") UUID profileId,
            @PathVariable UUID identityId,
            @Valid @RequestBody TenantUnlinkIdentityRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        TenantAuditMetadata audit = audit(body.reasonCode(), correlationId);
        var execution = commands.unlinkIdentity(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        UNLINK_ROUTE,
                        Map.of("id", profileId.toString(), "identityId", identityId.toString()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                        Map.of(),
                        correlationId),
                profileId,
                identityId,
                audit);
        var completed = ApiCommandResponses.requireCompleted(execution);
        return ApiCommandResponses.body(
                completed,
                CommandReceiptResponse.from(completed.representation().orElseThrow()));
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
