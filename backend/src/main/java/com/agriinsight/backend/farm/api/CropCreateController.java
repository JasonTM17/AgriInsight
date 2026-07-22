package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.application.CropCommandService;
import com.agriinsight.backend.farm.application.CropCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/crops")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CropCreateController {

    private static final String CROPS = ApiVersion.PREFIX + "/crops";

    private final CropCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public CropCreateController(
            CropCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<CropResponse> create(
            @Valid @RequestBody CropCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new CropCommands.Create(
                body.code(), body.displayName(), Optional.ofNullable(body.scientificName()),
                audit(body.reasonCode(), correlationId));
        var execution = commands.create(
                fingerprints.create(
                        principal, idempotencyKey, "POST", CROPS, Map.of(), Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "code", body.code(),
                                "displayName", body.displayName(),
                                "scientificName", Optional.ofNullable(body.scientificName()),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(), correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        CropResponse response = CropResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(CROPS + "/" + response.id()))
                .body(response);
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
