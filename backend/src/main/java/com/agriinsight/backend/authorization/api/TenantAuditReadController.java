package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditQuery;
import com.agriinsight.backend.authorization.application.TenantAuditReadService;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/audit-events")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantAuditReadController {

    private final TenantAuditReadService auditEvents;

    public TenantAuditReadController(TenantAuditReadService auditEvents) {
        this.auditEvents = auditEvents;
    }

    @GetMapping
    ResponseEntity<TenantAuditPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID actorProfileId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) TenantAuditEvent.Outcome outcome) {
        return ResponseEntity.ok(TenantAuditPageResponse.from(auditEvents.list(
                new TenantAuditQuery(
                        limit,
                        offset,
                        Optional.ofNullable(actorProfileId),
                        Optional.ofNullable(action),
                        Optional.ofNullable(targetType),
                        Optional.ofNullable(targetId),
                        Optional.ofNullable(outcome)))));
    }
}
