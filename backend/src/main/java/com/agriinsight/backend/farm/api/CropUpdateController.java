package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.CropCommandService;
import com.agriinsight.backend.farm.application.CropCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/crops")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CropUpdateController {

    private static final String CROP = ApiVersion.PREFIX + "/crops/{id}";

    private final CropCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public CropUpdateController(
            CropCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PatchMapping("/{id}")
    ResponseEntity<CropResponse> update(
            @PathVariable("id") UUID cropId,
            @Valid @RequestBody CropUpdateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new CropCommands.Update(
                Optional.ofNullable(body.code()), Optional.ofNullable(body.displayName()),
                scientificNamePatch(body.scientificName(), body.clearScientificName()),
                expected.value(),
                new TenantAuditMetadata(
                        Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.update(
                fingerprints.create(
                        principal, idempotencyKey, "PATCH", CROP,
                        Map.of("id", cropId.toString()), Map.of(), canonicalBody(body),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                cropId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        CropResponse response = CropResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private String canonicalBody(CropUpdateRequest body) {
        return CanonicalCommandBody.of(Map.of(
                "code", Optional.ofNullable(body.code()),
                "displayName", Optional.ofNullable(body.displayName()),
                "scientificName", Optional.ofNullable(body.scientificName()),
                "clearScientificName", body.clearScientificName(),
                "reasonCode", Optional.ofNullable(body.reasonCode())));
    }

    private Optional<Optional<String>> scientificNamePatch(String value, boolean clear) {
        return clear ? Optional.of(Optional.empty())
                : Optional.ofNullable(value).map(Optional::of);
    }
}
