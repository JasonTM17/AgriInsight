package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.ActivityLogCommandService;
import com.agriinsight.backend.operations.application.ActivityLogCommands;
import com.agriinsight.backend.operations.application.ActivityLogRecord;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
public class ActivityLogController {

    private static final String LOGS = ApiVersion.PREFIX + "/activities/{id}/logs";
    private static final String CORRECTIONS = LOGS + "/{logId}/corrections";

    private final ActivityLogCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public ActivityLogController(
            ActivityLogCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/logs")
    ResponseEntity<ActivityLogResponse> append(
            @PathVariable("id") UUID activityId,
            @Valid @RequestBody ActivityLogAppendRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new ActivityLogCommands.Append(
                body.employeeId(), body.occurredAt(), Optional.ofNullable(body.notes()),
                Optional.ofNullable(body.quantity()), Optional.ofNullable(body.unit()),
                Optional.ofNullable(body.evidenceUri()), audit(body.reasonCode(), correlationId));
        var execution = commands.append(
                fingerprints.create(
                        principal, idempotencyKey, "POST", LOGS,
                        Map.of("id", activityId.toString()), Map.of(), appendBody(command),
                        Map.of(), correlationId),
                activityId, command);
        return response(execution, activityId);
    }

    @PostMapping("/{id}/logs/{logId}/corrections")
    ResponseEntity<ActivityLogResponse> correct(
            @PathVariable("id") UUID activityId,
            @PathVariable UUID logId,
            @Valid @RequestBody ActivityLogCorrectionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new ActivityLogCommands.Correct(
                body.correctionKind(), body.occurredAt(), Optional.ofNullable(body.notes()),
                Optional.ofNullable(body.quantity()), Optional.ofNullable(body.unit()),
                Optional.ofNullable(body.evidenceUri()), body.correctionReason(),
                audit(body.reasonCode(), correlationId));
        var execution = commands.correct(
                fingerprints.create(
                        principal, idempotencyKey, "POST", CORRECTIONS,
                        Map.of("id", activityId.toString(), "logId", logId.toString()), Map.of(),
                        correctionBody(command), Map.of(), correlationId),
                activityId, logId, command);
        return response(execution, activityId);
    }

    private String appendBody(ActivityLogCommands.Append command) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("employeeId", command.employeeId()),
                Map.entry("occurredAt", command.occurredAt().toString()),
                Map.entry("notes", command.notes()),
                Map.entry("quantity", command.quantity()),
                Map.entry("unit", command.unit()),
                Map.entry("evidenceUri", command.evidenceUri()),
                Map.entry("reasonCode", command.audit().reasonCode())));
    }

    private String correctionBody(ActivityLogCommands.Correct command) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("correctionKind", command.correctionKind()),
                Map.entry("occurredAt", command.occurredAt().toString()),
                Map.entry("notes", command.notes()),
                Map.entry("quantity", command.quantity()),
                Map.entry("unit", command.unit()),
                Map.entry("evidenceUri", command.evidenceUri()),
                Map.entry("correctionReason", command.correctionReason()),
                Map.entry("reasonCode", command.audit().reasonCode())));
    }

    private ResponseEntity<ActivityLogResponse> response(
            CommandExecutionResult<ActivityLogRecord> execution,
            UUID activityId) {
        var completed = ApiCommandResponses.requireCompleted(execution);
        ActivityLogResponse response = ActivityLogResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(ApiVersion.PREFIX + "/activities/" + activityId
                        + "/logs/" + response.id()))
                .body(response);
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(
                Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
