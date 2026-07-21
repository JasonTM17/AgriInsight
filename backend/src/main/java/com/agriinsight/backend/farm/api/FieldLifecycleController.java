package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.FieldCommandService;
import com.agriinsight.backend.farm.application.FieldCommands;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.IfMatchVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping(ApiVersion.PREFIX + "/fields")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FieldLifecycleController {

    private static final String FIELDS = ApiVersion.PREFIX + "/fields";
    private static final String DEACTIVATE = FIELDS + "/{id}/deactivate";
    private static final String REACTIVATE = FIELDS + "/{id}/reactivate";

    private final FieldCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public FieldLifecycleController(
            FieldCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<FieldResponse> deactivate(
            @PathVariable("id") UUID fieldId,
            @Valid @RequestBody FieldLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(fieldId, body, idempotencyKey, ifMatch, principal, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<FieldResponse> reactivate(
            @PathVariable("id") UUID fieldId,
            @Valid @RequestBody FieldLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(fieldId, body, idempotencyKey, ifMatch, principal, request, true);
    }

    private ResponseEntity<FieldResponse> changeActive(
            UUID fieldId,
            FieldLifecycleRequest body,
            String idempotencyKey,
            String ifMatch,
            TenantPrincipal principal,
            HttpServletRequest request,
            boolean active) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        String route = active ? REACTIVATE : DEACTIVATE;
        var executionRequest = fingerprints.create(
                principal, idempotencyKey, "POST", route,
                Map.of("id", fieldId.toString()), Map.of(),
                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId);
        var command = new FieldCommands.Lifecycle(
                expected.value(),
                new TenantAuditMetadata(
                        Optional.of(body.reasonCode()), Optional.of(correlationId)));
        var execution = active
                ? commands.reactivate(executionRequest, fieldId, command)
                : commands.deactivate(executionRequest, fieldId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        FieldResponse response = FieldResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
