package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.application.EmployeeCommandService;
import com.agriinsight.backend.operations.application.EmployeeCommands;
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
@RequestMapping(ApiVersion.PREFIX + "/employees")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class EmployeeCreateController {

    private static final String EMPLOYEES = ApiVersion.PREFIX + "/employees";

    private final EmployeeCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public EmployeeCreateController(
            EmployeeCommandService commands, ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PostMapping
    ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody EmployeeCreateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        var command = new EmployeeCommands.Create(
                body.code(), body.displayName(), Optional.ofNullable(body.jobTitle()),
                new TenantAuditMetadata(Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.create(
                fingerprints.create(
                        principal, idempotencyKey, "POST", EMPLOYEES, Map.of(), Map.of(),
                        CanonicalCommandBody.of(Map.of(
                                "code", body.code(), "displayName", body.displayName(),
                                "jobTitle", Optional.ofNullable(body.jobTitle()),
                                "reasonCode", Optional.ofNullable(body.reasonCode()))),
                        Map.of(), correlationId),
                command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        EmployeeResponse response = EmployeeResponse.from(completed.representation().orElseThrow());
        return ResponseEntity.status(completed.responseStatus())
                .headers(ApiCommandResponses.headers(response.version()))
                .location(URI.create(EMPLOYEES + "/" + response.id()))
                .body(response);
    }
}
