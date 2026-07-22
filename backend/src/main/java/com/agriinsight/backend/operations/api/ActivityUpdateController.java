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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/activities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityUpdateController {

    private static final String ACTIVITY = ApiVersion.PREFIX + "/activities/{id}";

    private final ActivityCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public ActivityUpdateController(
            ActivityCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PatchMapping("/{id}")
    ResponseEntity<ActivityResponse> update(
            @PathVariable("id") UUID activityId,
            @Valid @RequestBody ActivityUpdateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new ActivityCommands.Update(
                Optional.ofNullable(body.activityType()), Optional.ofNullable(body.code()),
                Optional.ofNullable(body.title()), descriptionPatch(body.description(), body.clearDescription()),
                Optional.ofNullable(body.plannedStartAt()), Optional.ofNullable(body.dueAt()), expected.value(),
                new TenantAuditMetadata(Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.update(
                fingerprints.create(
                        principal, idempotencyKey, "PATCH", ACTIVITY,
                        Map.of("id", activityId.toString()), Map.of(), canonicalBody(body),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                activityId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        ActivityResponse response = ActivityResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private String canonicalBody(ActivityUpdateRequest body) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("activityType", Optional.ofNullable(body.activityType())),
                Map.entry("code", Optional.ofNullable(body.code())),
                Map.entry("title", Optional.ofNullable(body.title())),
                Map.entry("description", Optional.ofNullable(body.description())),
                Map.entry("clearDescription", body.clearDescription()),
                Map.entry("plannedStartAt", optionalInstant(body.plannedStartAt())),
                Map.entry("dueAt", optionalInstant(body.dueAt())),
                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode()))));
    }

    private Optional<Optional<String>> descriptionPatch(String value, boolean clear) {
        return clear ? Optional.of(Optional.empty()) : Optional.ofNullable(value).map(Optional::of);
    }

    private Optional<String> optionalInstant(Instant value) {
        return Optional.ofNullable(value).map(Instant::toString);
    }
}
