package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.FieldCommandService;
import com.agriinsight.backend.farm.application.FieldCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/farms/{farmId}/fields")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FieldCreateController {

    private static final String FARM_FIELDS = ApiVersion.PREFIX + "/farms/{farmId}/fields";
    private static final String FIELDS = ApiVersion.PREFIX + "/fields";

    private final FieldCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public FieldCreateController(
            FieldCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<FieldResponse> create(
            @PathVariable UUID farmId,
            @Valid @RequestBody FieldCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new FieldCommands.Create(
                farmId, body.code(), body.displayName(), body.areaHectares(),
                Optional.ofNullable(body.responsibleEmployeeId()), body.coordinates(),
                Optional.ofNullable(body.soilType()), Optional.ofNullable(body.irrigationType()),
                audit(body.reasonCode(), correlationId));
        var execution = commands.create(
                fingerprints.create(
                        principal, idempotencyKey, "POST", FARM_FIELDS,
                        Map.of("farmId", farmId.toString()), Map.of(), canonicalBody(body),
                        Map.of(), correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        FieldResponse response = FieldResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(FIELDS + "/" + response.id()))
                .body(response);
    }

    private String canonicalBody(FieldCreateRequest body) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("code", body.code()),
                Map.entry("displayName", body.displayName()),
                Map.entry("areaHectares", body.areaHectares()),
                Map.entry("responsibleEmployeeId", Optional.ofNullable(body.responsibleEmployeeId())),
                Map.entry("latitude", Optional.ofNullable(body.latitude())),
                Map.entry("longitude", Optional.ofNullable(body.longitude())),
                Map.entry("soilType", Optional.ofNullable(body.soilType())),
                Map.entry("irrigationType", Optional.ofNullable(body.irrigationType())),
                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode()))));
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
