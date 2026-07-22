package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.ActivityCommandService;
import com.agriinsight.backend.operations.application.ActivityCommands;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/activities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityCreateController {

    private static final String ACTIVITIES = ApiVersion.PREFIX + "/activities";

    private final ActivityCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public ActivityCreateController(
            ActivityCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<ActivityResponse> create(
            @Valid @RequestBody ActivityCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new ActivityCommands.Create(
                body.farmId(), body.fieldId(), body.seasonId(), body.activityType(),
                body.code(), body.title(), Optional.ofNullable(body.description()),
                body.plannedStartAt(), body.dueAt(),
                new TenantAuditMetadata(Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.create(
                fingerprints.create(
                        principal, idempotencyKey, "POST", ACTIVITIES, Map.of(), Map.of(),
                        CanonicalCommandBody.of(Map.ofEntries(
                                Map.entry("farmId", body.farmId()),
                                Map.entry("fieldId", body.fieldId()),
                                Map.entry("seasonId", body.seasonId()),
                                Map.entry("activityType", body.activityType()),
                                Map.entry("code", body.code()),
                                Map.entry("title", body.title()),
                                Map.entry("description", Optional.ofNullable(body.description())),
                                Map.entry("plannedStartAt", body.plannedStartAt().toString()),
                                Map.entry("dueAt", body.dueAt().toString()),
                                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode())))),
                        Map.of(), correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        ActivityResponse response = ActivityResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(ACTIVITIES + "/" + response.id()))
                .body(response);
    }
}
