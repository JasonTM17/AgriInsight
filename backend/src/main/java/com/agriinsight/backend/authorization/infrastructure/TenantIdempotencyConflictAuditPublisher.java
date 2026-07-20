package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.IdempotencyConflictPublisher;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class TenantIdempotencyConflictAuditPublisher implements IdempotencyConflictPublisher {

    private final TenantAuditPublisher auditPublisher;

    public TenantIdempotencyConflictAuditPublisher(TenantAuditPublisher auditPublisher) {
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    @Override
    public void publish(Conflict conflict) {
        Objects.requireNonNull(conflict, "conflict is required");
        ScopeContext scope = new ScopeContext(
                conflict.tenantId(),
                conflict.principalId(),
                ScopeContext.Type.TENANT,
                Optional.empty());
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                TenantAuditEvent.Action.IDEMPOTENCY_CONFLICT,
                TenantAuditEvent.TargetType.API_COMMAND,
                Optional.of(conflict.commandId()),
                Optional.of(conflict.routeTemplate()),
                Optional.of("IDEMPOTENCY_KEY_REUSED"),
                conflict.correlationId(),
                TenantAuditEvent.Outcome.CONFLICT));
    }
}
