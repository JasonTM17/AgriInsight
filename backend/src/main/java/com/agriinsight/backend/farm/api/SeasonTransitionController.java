package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.SeasonCommandService;
import com.agriinsight.backend.farm.application.SeasonCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/seasons")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SeasonTransitionController {

    private static final String TRANSITION = ApiVersion.PREFIX + "/seasons/{id}/transition";

    private final SeasonCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public SeasonTransitionController(
            SeasonCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/transition")
    ResponseEntity<SeasonResponse> transition(
            @PathVariable("id") UUID seasonId,
            @Valid @RequestBody SeasonTransitionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new SeasonCommands.Transition(
                body.targetStatus(), body.effectiveDate(), expected.value(),
                new TenantAuditMetadata(Optional.of(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.transition(
                fingerprints.create(
                        principal, idempotencyKey, "POST", TRANSITION,
                        Map.of("id", seasonId.toString()), Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "targetStatus", body.targetStatus(),
                                "effectiveDate", body.effectiveDate().toString(),
                                "reasonCode", body.reasonCode())),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                seasonId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        SeasonResponse response = SeasonResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
