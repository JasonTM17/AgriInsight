package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.FarmCommandService;
import com.agriinsight.backend.farm.application.FarmCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/farms")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FarmLifecycleController {

    private static final String FARMS = ApiVersion.PREFIX + "/farms";
    private static final String DEACTIVATE = FARMS + "/{id}/deactivate";
    private static final String REACTIVATE = FARMS + "/{id}/reactivate";

    private final FarmCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public FarmLifecycleController(
            FarmCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<FarmResponse> deactivate(
            @PathVariable("id") UUID farmId,
            @Valid @RequestBody FarmLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(farmId, body, idempotencyKey, ifMatch, principal, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<FarmResponse> reactivate(
            @PathVariable("id") UUID farmId,
            @Valid @RequestBody FarmLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(farmId, body, idempotencyKey, ifMatch, principal, request, true);
    }

    private ResponseEntity<FarmResponse> changeActive(
            UUID farmId,
            FarmLifecycleRequest body,
            String idempotencyKey,
            String ifMatch,
            TenantPrincipal principal,
            HttpServletRequest request,
            boolean active) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        String route = active ? REACTIVATE : DEACTIVATE;
        var executionRequest = fingerprints.create(
                principal,
                idempotencyKey,
                "POST",
                route,
                Map.of("id", farmId.toString()),
                Map.of(),
                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                correlationId);
        var command = new FarmCommands.Lifecycle(
                expected.value(),
                new TenantAuditMetadata(
                        Optional.of(body.reasonCode()),
                        Optional.of(correlationId)));
        var execution = active
                ? commands.reactivate(executionRequest, farmId, command)
                : commands.deactivate(executionRequest, farmId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        FarmResponse response = FarmResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
