package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentCommandService;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/warehouse-assignments")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class WarehouseAssignmentController {

    private static final String ASSIGNMENTS = ApiVersion.PREFIX + "/warehouse-assignments";
    private static final String REVOKE = ASSIGNMENTS + "/{id}/revoke";

    private final WarehouseAssignmentCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public WarehouseAssignmentController(
            WarehouseAssignmentCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<WarehouseAssignmentResponse> grant(
            @Valid @RequestBody WarehouseAssignmentGrantRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new WarehouseAssignmentCommands.Grant(
                body.userProfileId(),
                body.warehouseId(),
                expected.value(),
                audit(body.reasonCode(), correlationId));
        var execution = commands.grant(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        ASSIGNMENTS,
                        Map.of(),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "userProfileId", body.userProfileId().toString(),
                                "warehouseId", body.warehouseId().toString(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                command);
        return response(execution);
    }

    @PostMapping("/{id}/revoke")
    ResponseEntity<WarehouseAssignmentResponse> revoke(
            @PathVariable("id") UUID assignmentId,
            @Valid @RequestBody WarehouseAssignmentRevokeRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var execution = commands.revoke(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        REVOKE,
                        Map.of("id", assignmentId.toString()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                assignmentId,
                new WarehouseAssignmentCommands.Revoke(
                        expected.value(), audit(body.reasonCode(), correlationId)));
        return response(execution);
    }

    private ResponseEntity<WarehouseAssignmentResponse> response(
            com.agriinsight.backend.shared.application.CommandExecutionResult<
                    com.agriinsight.backend.inventory.application.WarehouseAssignmentRecord>
                    execution) {
        var completed = ApiCommandResponses.requireCompleted(execution);
        WarehouseAssignmentResponse response = WarehouseAssignmentResponse.from(
                completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(
                Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
