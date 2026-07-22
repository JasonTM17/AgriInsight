package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.EmployeeCommandService;
import com.agriinsight.backend.operations.application.EmployeeCommands;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/employees")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class EmployeeLifecycleController {

    private static final String EMPLOYEES = ApiVersion.PREFIX + "/employees";
    private static final String DEACTIVATE = EMPLOYEES + "/{id}/deactivate";
    private static final String REACTIVATE = EMPLOYEES + "/{id}/reactivate";

    private final EmployeeCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public EmployeeLifecycleController(
            EmployeeCommandService commands, ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping("/{id}/deactivate")
    ResponseEntity<EmployeeResponse> deactivate(
            @PathVariable("id") UUID employeeId,
            @Valid @RequestBody EmployeeLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(employeeId, body, idempotencyKey, ifMatch, principal, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ResponseEntity<EmployeeResponse> reactivate(
            @PathVariable("id") UUID employeeId,
            @Valid @RequestBody EmployeeLifecycleRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        return changeActive(employeeId, body, idempotencyKey, ifMatch, principal, request, true);
    }

    private ResponseEntity<EmployeeResponse> changeActive(
            UUID employeeId,
            EmployeeLifecycleRequest body,
            String idempotencyKey,
            String ifMatch,
            TenantPrincipal principal,
            HttpServletRequest request,
            boolean active) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        String route = active ? REACTIVATE : DEACTIVATE;
        var commandRequest = fingerprints.create(
                principal, idempotencyKey, "POST", route,
                Map.of("id", employeeId.toString()), Map.of(),
                CanonicalCommandBody.of(Map.of("reasonCode", body.reasonCode())),
                Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId);
        var command = new EmployeeCommands.Lifecycle(
                expected.value(), new TenantAuditMetadata(
                        Optional.of(body.reasonCode()), Optional.of(correlationId)));
        var execution = active
                ? commands.reactivate(commandRequest, employeeId, command)
                : commands.deactivate(commandRequest, employeeId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        EmployeeResponse response = EmployeeResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }
}
