package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.cost.application.CostCommands;
import com.agriinsight.backend.cost.application.CostCorrectionRecord;
import com.agriinsight.backend.cost.application.OperatingCostCommandService;
import com.agriinsight.backend.cost.application.OperatingCostRecord;
import com.agriinsight.backend.shared.api.ApiCommandFingerprintFactory;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.RequestCorrelation;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.domain.CanonicalCommandBody;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping(ApiVersion.PREFIX + "/cost-entries")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class OperatingCostMutationController {

    private static final String ENTRIES = ApiVersion.PREFIX + "/cost-entries";
    private static final String CORRECTIONS = ENTRIES + "/{id}/corrections";
    private final OperatingCostCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public OperatingCostMutationController(
            OperatingCostCommandService commands,
            ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @Operation(
            summary = "Post an operating cost entry",
            description = "Appends one positive VND posting to the operating-cost ledger. "
                    + "Procurement spend and inventory value remain separate lenses.")
    @PostMapping
    ResponseEntity<OperatingCostResponse> post(
            @Valid @RequestBody OperatingCostPostRequest body,
            @Parameter(required = true, example = "cost-post-2027-00042")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        CostCommands.Post command = body.toCommand(audit(body.reasonCode(), correlationId));
        var execution = commands.post(
                fingerprints.create(
                        principal, idempotencyKey, "POST", ENTRIES,
                        Map.of(), Map.of(), canonical(command), Map.of(), correlationId),
                command);
        return postingResponse(execution);
    }

    @Operation(
            summary = "Correct an operating cost entry",
            description = "Atomically appends an exact reversal and one validated replacement. "
                    + "The original ledger row is never updated or deleted.")
    @PostMapping("/{id}/corrections")
    ResponseEntity<CostCorrectionResponse> correct(
            @PathVariable UUID id,
            @Valid @RequestBody OperatingCostCorrectionRequest body,
            @Parameter(required = true, example = "cost-correct-2027-00042")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        CostCommands.Correct command = body.toCommand(audit(body.reasonCode(), correlationId));
        var execution = commands.correct(
                fingerprints.create(
                        principal, idempotencyKey, "POST", CORRECTIONS,
                        Map.of("id", id.toString()), Map.of(), canonical(command),
                        Map.of(), correlationId),
                id,
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        CostCorrectionResponse response = CostCorrectionResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.replacement().version()))
                .location(URI.create(ENTRIES + "/" + response.replacement().id()))
                .body(response);
    }

    private ResponseEntity<OperatingCostResponse> postingResponse(
            CommandExecutionResult<OperatingCostRecord> execution) {
        var completed = ApiCommandResponses.requireCompleted(execution);
        OperatingCostResponse response = OperatingCostResponse.from(
                completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(ENTRIES + "/" + response.id()))
                .body(response);
    }

    private String canonical(CostCommands.Post command) {
        return canonical(
                command.target().type(), command.target().id(), command.category(),
                command.amountVnd(), command.occurredAt(), command.description(),
                command.sourceReference(), Optional.empty(), command.audit());
    }

    private String canonical(CostCommands.Correct command) {
        return canonical(
                command.target().type(), command.target().id(), command.category(),
                command.amountVnd(), command.occurredAt(), command.description(),
                command.sourceReference(), Optional.of(command.correctionReason()), command.audit());
    }

    private String canonical(
            Object targetType,
            Object targetId,
            Object category,
            Object amount,
            Object occurredAt,
            Object description,
            Object sourceReference,
            Object correctionReason,
            TenantAuditMetadata audit) {
        return CanonicalCommandBody.of(Map.ofEntries(
                Map.entry("targetType", targetType), Map.entry("targetId", targetId),
                Map.entry("category", category), Map.entry("amountVnd", amount),
                Map.entry("occurredAt", occurredAt.toString()),
                Map.entry("description", description),
                Map.entry("sourceReference", sourceReference),
                Map.entry("correctionReason", correctionReason),
                Map.entry("reasonCode", audit.reasonCode())));
    }

    private TenantAuditMetadata audit(String reasonCode, String correlationId) {
        return new TenantAuditMetadata(
                Optional.ofNullable(reasonCode), Optional.of(correlationId));
    }
}
