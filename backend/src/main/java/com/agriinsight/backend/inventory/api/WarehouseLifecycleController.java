package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.WarehouseCommandService;
import com.agriinsight.backend.inventory.application.WarehouseCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/warehouses")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class WarehouseLifecycleController {

    private static final String WAREHOUSES = ApiVersion.PREFIX + "/warehouses";
    private static final String DEACTIVATE = WAREHOUSES + "/{id}/deactivate";
    private static final String REACTIVATE = WAREHOUSES + "/{id}/reactivate";

    private final WarehouseCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public WarehouseLifecycleController(
            WarehouseCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<WarehouseResponse> deactivate(
            @PathVariable("id") UUID warehouseId,
            @Valid @RequestBody WarehouseLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(
                warehouseId, body, idempotencyKey, ifMatch, principal, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<WarehouseResponse> reactivate(
            @PathVariable("id") UUID warehouseId,
            @Valid @RequestBody WarehouseLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(
                warehouseId, body, idempotencyKey, ifMatch, principal, request, true);
    }

    private ResponseEntity<WarehouseResponse> changeActive(
            UUID warehouseId,
            WarehouseLifecycleRequest body,
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
                Map.of("id", warehouseId.toString()),
                Map.of(),
                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                correlationId);
        var command = new WarehouseCommands.Lifecycle(
                expected.value(),
                new TenantAuditMetadata(
                        Optional.of(body.reasonCode()), Optional.of(correlationId)));
        var execution = active
                ? commands.reactivate(executionRequest, warehouseId, command)
                : commands.deactivate(executionRequest, warehouseId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        WarehouseResponse response = WarehouseResponse.from(
                completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
