package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.FarmAssignmentCommandService;
import com.agriinsight.backend.farm.application.FarmAssignmentCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/farm-assignments")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FarmAssignmentController {

    private static final String ASSIGNMENTS = ApiVersion.PREFIX + "/farm-assignments";
    private static final String REVOKE = ASSIGNMENTS + "/{id}/revoke";

    private final FarmAssignmentCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public FarmAssignmentController(
            FarmAssignmentCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<FarmAssignmentResponse> grant(
            @Valid @RequestBody FarmAssignmentGrantRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new FarmAssignmentCommands.Grant(
                body.userProfileId(),
                body.farmId(),
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
                                "farmId", body.farmId().toString(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                command);
        return response(execution);
    }

    @PostMapping("/{id}/revoke")
    ResponseEntity<FarmAssignmentResponse> revoke(
            @PathVariable("id") UUID assignmentId,
            @Valid @RequestBody FarmAssignmentRevokeRequest body,
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
                new FarmAssignmentCommands.Revoke(
                        expected.value(), audit(body.reasonCode(), correlationId)));
        return response(execution);
    }

    private ResponseEntity<FarmAssignmentResponse> response(
            com.agriinsight.backend.shared.application.CommandExecutionResult<
                    com.agriinsight.backend.farm.application.FarmAssignmentRecord> execution) {
        var completed = ApiCommandResponses.requireCompleted(execution);
        FarmAssignmentResponse response =
                FarmAssignmentResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(
                Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
