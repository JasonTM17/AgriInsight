package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.ActivityAssignmentCommandService;
import com.agriinsight.backend.operations.application.ActivityAssignmentCommands;
import com.agriinsight.backend.operations.application.ActivityAssignmentRecord;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.IfMatchVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
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
@RequestMapping(ApiVersion.PREFIX + "/activities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityAssignmentController {

    private static final String ASSIGNMENTS = ApiVersion.PREFIX + "/activities/{id}/assignments";
    private static final String REVOKE = ASSIGNMENTS + "/{assignmentId}/revoke";

    private final ActivityAssignmentCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public ActivityAssignmentController(
            ActivityAssignmentCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/assignments")
    ResponseEntity<ActivityAssignmentResponse> grant(
            @PathVariable("id") UUID activityId,
            @Valid @RequestBody ActivityAssignmentGrantRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new ActivityAssignmentCommands.Grant(
                body.employeeId(), expected.value(), audit(body.reasonCode(), correlationId));
        var execution = commands.grant(
                fingerprints.create(
                        principal, idempotencyKey, "POST", ASSIGNMENTS,
                        Map.of("id", activityId.toString()), Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "employeeId", body.employeeId(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                activityId, command);
        return response(execution);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/revoke")
    ResponseEntity<ActivityAssignmentResponse> revoke(
            @PathVariable("id") UUID activityId,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody ActivityAssignmentRevokeRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var execution = commands.revoke(
                fingerprints.create(
                        principal, idempotencyKey, "POST", REVOKE,
                        Map.of("id", activityId.toString(), "assignmentId", assignmentId.toString()), Map.of(),
                        CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                activityId, assignmentId,
                new ActivityAssignmentCommands.Revoke(
                        expected.value(), audit(body.reasonCode(), correlationId)));
        return response(execution);
    }

    private ResponseEntity<ActivityAssignmentResponse> response(
            CommandExecutionResult<ActivityAssignmentRecord> execution) {
        var completed = ApiCommandResponses.requireCompleted(execution);
        ActivityAssignmentResponse response =
                ActivityAssignmentResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(
                Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
