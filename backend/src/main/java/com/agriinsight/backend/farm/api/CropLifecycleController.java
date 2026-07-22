package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.CropCommandService;
import com.agriinsight.backend.farm.application.CropCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/crops")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CropLifecycleController {

    private static final String CROPS = ApiVersion.PREFIX + "/crops";
    private static final String DEACTIVATE = CROPS + "/{id}/deactivate";
    private static final String REACTIVATE = CROPS + "/{id}/reactivate";

    private final CropCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public CropLifecycleController(
            CropCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<CropResponse> deactivate(
            @PathVariable("id") UUID cropId,
            @Valid @RequestBody CropLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(cropId, body, idempotencyKey, ifMatch, principal, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<CropResponse> reactivate(
            @PathVariable("id") UUID cropId,
            @Valid @RequestBody CropLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(cropId, body, idempotencyKey, ifMatch, principal, request, true);
    }

    private ResponseEntity<CropResponse> changeActive(
            UUID cropId,
            CropLifecycleRequest body,
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
                Map.of("id", cropId.toString()), Map.of(),
                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId);
        var command = new CropCommands.Lifecycle(
                expected.value(),
                new TenantAuditMetadata(
                        Optional.of(body.reasonCode()), Optional.of(correlationId)));
        var execution = active
                ? commands.reactivate(executionRequest, cropId, command)
                : commands.deactivate(executionRequest, cropId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        CropResponse response = CropResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
