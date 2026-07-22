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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/employees")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class EmployeeUpdateController {

    private static final String EMPLOYEE = ApiVersion.PREFIX + "/employees/{id}";

    private final EmployeeCommandService commands;
    private final ApiCommandFingerprintFactory fingerprints;

    public EmployeeUpdateController(
            EmployeeCommandService commands, ApiCommandFingerprintFactory fingerprints) {
        this.commands = commands;
        this.fingerprints = fingerprints;
    }

    @PatchMapping("/{id}")
    ResponseEntity<EmployeeResponse> update(
            @PathVariable("id") UUID employeeId,
            @Valid @RequestBody EmployeeUpdateRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @AuthenticationPrincipal TenantPrincipal principal,
            HttpServletRequest request) {
        String correlationId = RequestCorrelation.resolve(request);
        IfMatchVersion expected = IfMatchVersion.parse(ifMatch);
        var command = new EmployeeCommands.Update(
                Optional.ofNullable(body.code()), Optional.ofNullable(body.displayName()),
                jobTitlePatch(body.jobTitle(), body.clearJobTitle()), expected.value(),
                new TenantAuditMetadata(Optional.ofNullable(body.reasonCode()), Optional.of(correlationId)));
        var execution = commands.update(
                fingerprints.create(
                        principal, idempotencyKey, "PATCH", EMPLOYEE,
                        Map.of("id", employeeId.toString()), Map.of(), canonicalBody(body),
                        Map.of(HttpHeaders.IF_MATCH, expected.canonicalHeaderValue()), correlationId),
                employeeId, command);
        var completed = ApiCommandResponses.requireCompleted(execution);
        EmployeeResponse response = EmployeeResponse.from(completed.representation().orElseThrow());
        return ApiCommandResponses.body(completed, response, response.version());
    }

    private String canonicalBody(EmployeeUpdateRequest body) {
        return CanonicalCommandBody.of(Map.of(
                "code", Optional.ofNullable(body.code()),
                "displayName", Optional.ofNullable(body.displayName()),
                "jobTitle", Optional.ofNullable(body.jobTitle()),
                "clearJobTitle", body.clearJobTitle(),
                "reasonCode", Optional.ofNullable(body.reasonCode())));
    }

    private Optional<Optional<String>> jobTitlePatch(String value, boolean clear) {
        return clear ? Optional.of(Optional.empty()) : Optional.ofNullable(value).map(Optional::of);
    }
}
