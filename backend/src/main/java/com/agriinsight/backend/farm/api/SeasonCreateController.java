package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.SeasonCommandService;
import com.agriinsight.backend.farm.application.SeasonCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/seasons")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SeasonCreateController {

    private static final String SEASONS = ApiVersion.PREFIX + "/seasons";

    private final SeasonCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public SeasonCreateController(
            SeasonCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<SeasonResponse> create(
            @Valid @RequestBody SeasonCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new SeasonCommands.Create(
                body.farmId(), body.fieldId(), body.cropId(), body.code(), body.displayName(),
                Optional.ofNullable(body.varietyName()), body.plannedStartDate(), body.plannedEndDate(),
                body.plantedAreaHectares(), Optional.ofNullable(body.budgetVnd()),
                audit(body.reasonCode(), correlationId));
        var execution = commands.create(
                fingerprints.create(
                        principal, idempotencyKey, "POST", SEASONS, Map.of(), Map.of(),
                        CanonicalCommandBody.of(Map.ofEntries(
                                Map.entry("farmId", body.farmId()),
                                Map.entry("fieldId", body.fieldId()),
                                Map.entry("cropId", body.cropId()),
                                Map.entry("code", body.code()),
                                Map.entry("displayName", body.displayName()),
                                Map.entry("varietyName", Optional.ofNullable(body.varietyName())),
                                Map.entry("plannedStartDate", body.plannedStartDate().toString()),
                                Map.entry("plannedEndDate", body.plannedEndDate().toString()),
                                Map.entry("plantedAreaHectares", body.plantedAreaHectares()),
                                Map.entry("budgetVnd", Optional.ofNullable(body.budgetVnd())),
                                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode())))),
                        Map.of(), correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        SeasonResponse response = SeasonResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(SEASONS + "/" + response.id()))
                .body(response);
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
