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
import java.math.BigDecimal;
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
@RequestMapping(ApiVersion.PREFIX + "/seasons")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SeasonUpdateController {

    private static final String SEASON = ApiVersion.PREFIX + "/seasons/{id}";

    private final SeasonCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public SeasonUpdateController(
            SeasonCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PatchMapping("/{id}")
    ResponseEntity<SeasonResponse> update(
            @PathVariable("id") UUID seasonId,
            @Valid @RequestBody SeasonUpdateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new SeasonCommands.Update(
                Optional.ofNullable(body.code()),
                Optional.ofNullable(body.displayName()),
                textPatch(body.varietyName(), body.clearVarietyName()),
                Optional.ofNullable(body.plannedStartDate()),
                Optional.ofNullable(body.plannedEndDate()),
                Optional.ofNullable(body.plantedAreaHectares()),
                decimalPatch(body.budgetVnd(), body.clearBudgetVnd()),
                expected.value(),
                new TenantAuditMetadata(
                        Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.update(
                fingerprints.create(
                        principal, idempotencyKey, "PATCH", SEASON,
                        Map.of("id", seasonId.toString()), Map.of(),
                        canonicalBody(body),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                seasonId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        SeasonResponse response = SeasonResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private String canonicalBody(SeasonUpdateRequest body) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("code", Optional.ofNullable(body.code())),
                Map.entry("displayName", Optional.ofNullable(body.displayName())),
                Map.entry("varietyName", Optional.ofNullable(body.varietyName())),
                Map.entry("clearVarietyName", body.clearVarietyName()),
                Map.entry("plannedStartDate", Optional.ofNullable(body.plannedStartDate()).map(Object::toString)),
                Map.entry("plannedEndDate", Optional.ofNullable(body.plannedEndDate()).map(Object::toString)),
                Map.entry("plantedAreaHectares", Optional.ofNullable(body.plantedAreaHectares())),
                Map.entry("budgetVnd", Optional.ofNullable(body.budgetVnd())),
                Map.entry("clearBudgetVnd", body.clearBudgetVnd()),
                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode()))));
    }

    private Optional<Optional<String>> textPatch(String value, boolean clear) {
        return clear ? Optional.of(Optional.empty())
                : Optional.ofNullable(value).map(Optional::of);
    }

    private Optional<Optional<BigDecimal>> decimalPatch(BigDecimal value, boolean clear) {
        return clear ? Optional.of(Optional.empty())
                : Optional.ofNullable(value).map(Optional::of);
    }
}
