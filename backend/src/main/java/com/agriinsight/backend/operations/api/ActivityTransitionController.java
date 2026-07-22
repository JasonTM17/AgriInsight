package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.ActivityCommandService;
import com.agriinsight.backend.operations.application.ActivityCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/activities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityTransitionController {

    private static final String TRANSITION = ApiVersion.PREFIX + "/activities/{id}/transition";

    private final ActivityCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public ActivityTransitionController(
            ActivityCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/transition")
    ResponseEntity<ActivityResponse> transition(
            @PathVariable("id") UUID activityId,
            @Valid @RequestBody ActivityTransitionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new ActivityCommands.Transition(
                body.targetStatus(), body.effectiveAt(), expected.value(),
                new TenantAuditMetadata(Optional.of(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.transition(
                fingerprints.create(
                        principal, idempotencyKey, "POST", TRANSITION,
                        Map.of("id", activityId.toString()), Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "targetStatus", body.targetStatus(),
                                "effectiveAt", body.effectiveAt().toString(),
                                "reasonCode", body.reasonCode())),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                activityId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        ActivityResponse response = ActivityResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
