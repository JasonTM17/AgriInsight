package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.SupplierCommandService;
import com.agriinsight.backend.inventory.application.SupplierCommands;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.IfMatchVersion;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/suppliers")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SupplierMutationController {

    private static final String SUPPLIERS = ApiVersion.PREFIX + "/suppliers";
    private static final String SUPPLIER = SUPPLIERS + "/{id}";

    private final SupplierCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public SupplierMutationController(
            SupplierCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<SupplierResponse> create(
            @Valid @RequestBody SupplierCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new SupplierCommands.Create(
                body.code(),
                body.displayName(),
                audit(body.reasonCode(), correlationId));
        var execution = commands.create(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "POST",
                        SUPPLIERS,
                        Map.of(),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "code", body.code(),
                                "displayName", body.displayName(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(),
                        correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        SupplierResponse response = SupplierResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(SUPPLIERS + "/" + response.id()))
                .body(response);
    }

    @PatchMapping("/{id}")
    ResponseEntity<SupplierResponse> update(
            @PathVariable("id") UUID supplierId,
            @Valid @RequestBody SupplierUpdateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new SupplierCommands.Update(
                Optional.ofNullable(body.code()),
                Optional.ofNullable(body.displayName()),
                expected.value(),
                audit(body.reasonCode(), correlationId));
        var execution = commands.update(
                fingerprints.create(
                        principal,
                        idempotencyKey,
                        "PATCH",
                        SUPPLIER,
                        Map.of("id", supplierId.toString()),
                        Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "code", Optional.ofNullable(body.code()),
                                "displayName", Optional.ofNullable(body.displayName()),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                supplierId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        SupplierResponse response = SupplierResponse.from(
                completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
