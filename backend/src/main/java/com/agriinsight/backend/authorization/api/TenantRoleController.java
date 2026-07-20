package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentCommands;
import com.agriinsight.backend.authorization.application.TenantRoleCommandService;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.IfMatchVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
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
@RequestMapping(ApiVersion.PREFIX + "/users/{id}/roles")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantRoleController {

    private static final String GRANT_ROUTE = ApiVersion.PREFIX + "/users/{id}/roles";
    private static final String REVOKE_ROUTE = GRANT_ROUTE + "/{roleCode}/revoke";

    private final TenantRoleCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public TenantRoleController(
            TenantRoleCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<TenantRoleResponse> grant(
            @PathVariable("id") UUID profileId,
            @Valid @RequestBody TenantRoleGrantRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        Role role = parseRole(body.roleCode());
        var command = new TenantRoleAssignmentCommands.Grant(
                role,
                expected.value(),
                audit(body.reasonCode(), correlationId));
        var execution = commands.grant(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        GRANT_ROUTE,
                        Map.of("id", profileId.toString()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "reasonCode", Optional.ofNullable(body.reasonCode()),
                                "roleCode", role.name())),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                profileId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        TenantRoleResponse response = TenantRoleResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(
                completed,
                response,
                response.version());
    }

    @PostMapping("/{roleCode}/revoke")
    ResponseEntity<TenantRoleResponse> revoke(
            @PathVariable("id") UUID profileId,
            @PathVariable String roleCode,
            @Valid @RequestBody TenantRoleRevokeRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        Role role = parseRole(roleCode);
        var execution = commands.revoke(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        REVOKE_ROUTE,
                        Map.of("id", profileId.toString(), "roleCode", role.name()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                profileId,
                role,
                new TenantRoleAssignmentCommands.Revoke(
                        expected.value(),
                        audit(body.reasonCode(), correlationId)));
        var completed = ApiCommandResponses.requireCompleted(execution);
        TenantRoleResponse response = TenantRoleResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(
                completed,
                response,
                response.version());
    }

    private Role parseRole(String rawRole) {
        String normalized = rawRole == null ? null : rawRole.strip().toUpperCase(Locale.ROOT);
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("roleCode is not a supported fixed role", exception);
        }
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
