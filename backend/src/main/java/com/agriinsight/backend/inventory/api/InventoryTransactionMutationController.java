package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommandService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/inventory/transactions")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class InventoryTransactionMutationController {

    private static final String TRANSACTIONS = ApiVersion.PREFIX + "/inventory/transactions";
    private static final String REVERSALS = TRANSACTIONS + "/{id}/reversals";

    private final InventoryTransactionCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public InventoryTransactionMutationController(
            InventoryTransactionCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<InventoryTransactionResponse> post(
            @Valid @RequestBody InventoryTransactionPostRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = body.toCommand(audit(body.reasonCode(), correlationId));
        var execution = commands.post(
                fingerprints.create(
                        principal, idempotencyKey, "POST", TRANSACTIONS,
                        Map.of(), Map.of(), canonicalPosting(body), Map.of(), correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        InventoryTransactionResponse response = InventoryTransactionResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(TRANSACTIONS + "/" + response.id()))
                .body(response);
    }

    @PostMapping("/{id}/reversals")
    ResponseEntity<InventoryTransactionResponse> reverse(
            @PathVariable("id") UUID transactionId,
            @Valid @RequestBody InventoryReversalRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = body.toCommand(
                expected.value(), audit(body.reasonCode(), correlationId));
        var execution = commands.reverse(
                fingerprints.create(
                        principal, idempotencyKey, "POST", REVERSALS,
                        Map.of("id", transactionId.toString()), Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "quantityBase", body.quantityBase(),
                                "reason", body.reason(),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()),
                        correlationId),
                transactionId,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        InventoryTransactionResponse response = InventoryTransactionResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(TRANSACTIONS + "/" + response.id()))
                .body(response);
    }

    private String canonicalPosting(InventoryTransactionPostRequest body) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("kind", body.kind()),
                Map.entry("warehouseId", body.warehouseId()),
                Map.entry("materialId", body.materialId()),
                Map.entry("supplierId", Optional.ofNullable(body.supplierId())),
                Map.entry("quantityBase", body.quantityBase()),
                Map.entry("unitCostVnd", Optional.ofNullable(body.unitCostVnd())),
                Map.entry("batchCode", Optional.ofNullable(body.batchCode())),
                Map.entry("expiryDate", Optional.ofNullable(body.expiryDate())
                        .map(Object::toString)),
                Map.entry("stockLotId", Optional.ofNullable(body.stockLotId())),
                Map.entry("occurredAt", body.occurredAt().toString()),
                Map.entry("reason", Optional.ofNullable(body.reason())),
                Map.entry("referenceCode", Optional.ofNullable(body.referenceCode())),
                Map.entry("reasonCode", Optional.ofNullable(body.reasonCode()))));
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
