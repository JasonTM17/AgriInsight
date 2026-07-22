package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.HarvestCommandService;
import com.agriinsight.backend.operations.application.HarvestCommands;
import com.agriinsight.backend.operations.application.HarvestRecord;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
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
@RequestMapping(ApiVersion.PREFIX + "/harvests")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class HarvestCommandController {

    private static final String HARVESTS = ApiVersion.PREFIX + "/harvests";
    private static final String CORRECTIONS = HARVESTS + "/{id}/corrections";
    private final HarvestCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public HarvestCommandController(
            HarvestCommandService commands, ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<HarvestResponse> post(
            @Valid @RequestBody HarvestPostRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new HarvestCommands.Post(
                body.farmId(), body.fieldId(), body.seasonId(), body.cropId(), body.occurredOn(),
                body.unit().toKilograms(body.quantity(), "quantity"),
                body.unit().toKilograms(body.wasteQuantity(), "wasteQuantity"),
                Optional.ofNullable(body.qualityGrade()), Optional.ofNullable(body.revenueVnd()),
                audit(body.reasonCode(), correlationId));
        var execution = commands.post(fingerprints.create(
                principal, idempotencyKey, "POST", HARVESTS, Map.of(), Map.of(),
                canonical(command), Map.of(), correlationId), command);
        return response(execution);
    }

    @PostMapping("/{id}/corrections")
    ResponseEntity<HarvestResponse> correct(
            @PathVariable UUID id,
            @Valid @RequestBody HarvestCorrectionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new HarvestCommands.Correct(
                body.correctionKind(), body.occurredOn(), body.quantityKg(),
                body.wasteQuantityKg(), Optional.ofNullable(body.qualityGrade()),
                Optional.ofNullable(body.revenueVnd()), body.correctionReason(),
                audit(body.reasonCode(), correlationId));
        var execution = commands.correct(fingerprints.create(
                principal, idempotencyKey, "POST", CORRECTIONS,
                Map.of("id", id.toString()), Map.of(), canonical(command), Map.of(), correlationId),
                id, command);
        return response(execution);
    }

    private String canonical(HarvestCommands.Post command) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("farmId", command.farmId()), Map.entry("fieldId", command.fieldId()),
                Map.entry("seasonId", command.seasonId()), Map.entry("cropId", command.cropId()),
                Map.entry("occurredOn", command.occurredOn().toString()),
                Map.entry("quantityKg", command.quantityKg()),
                Map.entry("wasteQuantityKg", command.wasteQuantityKg()),
                Map.entry("qualityGrade", command.qualityGrade()),
                Map.entry("revenueVnd", command.revenueVnd()),
                Map.entry("reasonCode", command.audit().reasonCode())));
    }

    private String canonical(HarvestCommands.Correct command) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("correctionKind", command.correctionKind()),
                Map.entry("occurredOn", command.occurredOn().toString()),
                Map.entry("quantityKg", command.quantityKg()),
                Map.entry("wasteQuantityKg", command.wasteQuantityKg()),
                Map.entry("qualityGrade", command.qualityGrade()),
                Map.entry("revenueVnd", command.revenueVnd()),
                Map.entry("correctionReason", command.correctionReason()),
                Map.entry("reasonCode", command.audit().reasonCode())));
    }

    private ResponseEntity<HarvestResponse> response(
            CommandExecutionResult<HarvestRecord> execution) {
        var completed = ApiCommandResponses.requireCompleted(execution);
        HarvestResponse response = HarvestResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(HARVESTS + "/" + response.id()))
                .body(response);
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(
                Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
