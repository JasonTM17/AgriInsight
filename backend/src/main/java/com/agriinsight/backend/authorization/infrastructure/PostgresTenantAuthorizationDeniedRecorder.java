package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Persists tenant-resolved denials in an independent transaction so a rejected unit rolls back safely. */
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresTenantAuthorizationDeniedRecorder implements TenantAuthorizationDeniedRecorder {

    private final TenantContextBinder contextBinder;
    private final TenantAuditPublisher auditPublisher;
    private final TransactionTemplate transaction;

    public PostgresTenantAuthorizationDeniedRecorder(
            TenantContextBinder contextBinder,
            TenantAuditPublisher auditPublisher,
            PlatformTransactionManager transactionManager) {
        this.contextBinder = Objects.requireNonNull(contextBinder, "contextBinder is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
        this.transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager is required"));
        this.transaction.setName("tenant-authorization-denial-audit");
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(Decision decision) {
        Objects.requireNonNull(decision, "decision is required");
        transaction.executeWithoutResult(status -> {
            contextBinder.bind(decision.tenantId());
            ScopeContext scope = new ScopeContext(
                    decision.tenantId(),
                    decision.principalId(),
                    ScopeContext.Type.TENANT,
                    Optional.empty());
            auditPublisher.publish(new TenantAuditEvent(
                    scope,
                    TenantAuditEvent.Action.AUTHORIZATION_DENIED,
                    TenantAuditEvent.TargetType.API_COMMAND,
                    decision.targetId(),
                    Optional.of(decision.targetReference()),
                    Optional.of(decision.reasonCode()),
                    decision.correlationId(),
                    TenantAuditEvent.Outcome.DENIED));
        });
    }
}
