package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.FieldCommandService;
import com.agriinsight.backend.farm.application.FieldCommands;
import com.agriinsight.backend.farm.domain.Field;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/fields")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FieldUpdateController {

    private static final String FIELD = ApiVersion.PREFIX + "/fields/{id}";

    private final FieldCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public FieldUpdateController(
            FieldCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PatchMapping("/{id}")
    ResponseEntity<FieldResponse> update(
            @PathVariable("id") UUID fieldId,
            @Valid @RequestBody FieldUpdateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new FieldCommands.Update(
                Optional.ofNullable(body.code()), Optional.ofNullable(body.displayName()),
                Optional.ofNullable(body.areaHectares()),
                idPatch(body.responsibleEmployeeId(), body.clearResponsibleEmployeeId()),
                coordinatesPatch(body.coordinates(), body.clearCoordinates()),
                textPatch(body.soilType(), body.clearSoilType()),
                textPatch(body.irrigationType(), body.clearIrrigationType()),
                expected.value(),
                new TenantAuditMetadata(
                        Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.update(
                fingerprints.create(
                        principal, idempotencyKey, "PATCH", FIELD,
                        Map.of("id", fieldId.toString()), Map.of(), canonicalBody(body),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                fieldId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        FieldResponse response = FieldResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private String canonicalBody(FieldUpdateRequest body) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("code", Optional.ofNullable(body.code())),
                Map.entry("displayName", Optional.ofNullable(body.displayName())),
                Map.entry("areaHectares", Optional.ofNullable(body.areaHectares())),
                Map.entry("responsibleEmployeeId", Optional.ofNullable(body.responsibleEmployeeId())),
                Map.entry("clearResponsibleEmployeeId", body.clearResponsibleEmployeeId()),
                Map.entry("latitude", Optional.ofNullable(body.latitude())),
                Map.entry("longitude", Optional.ofNullable(body.longitude())),
                Map.entry("clearCoordinates", body.clearCoordinates()),
                Map.entry("soilType", Optional.ofNullable(body.soilType())),
                Map.entry("clearSoilType", body.clearSoilType()),
                Map.entry("irrigationType", Optional.ofNullable(body.irrigationType())),
                Map.entry("clearIrrigationType", body.clearIrrigationType()),
                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode()))));
    }

    private Optional<Optional<UUID>> idPatch(UUID value, boolean clear) {
        return clear ? Optional.of(Optional.empty()) : Optional.ofNullable(value).map(Optional::of);
    }

    private Optional<Optional<Field.Coordinates>> coordinatesPatch(
            Optional<Field.Coordinates> value,
            boolean clear) {
        return clear ? Optional.of(Optional.empty()) : value.map(Optional::of);
    }

    private Optional<Optional<String>> textPatch(String value, boolean clear) {
        return clear ? Optional.of(Optional.empty()) : Optional.ofNullable(value).map(Optional::of);
    }
}
