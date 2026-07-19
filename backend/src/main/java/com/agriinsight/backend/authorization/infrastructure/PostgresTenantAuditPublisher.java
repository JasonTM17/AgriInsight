package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class PostgresTenantAuditPublisher implements TenantAuditPublisher {

    private static final String INSERT_AUDIT_EVENT = """
            INSERT INTO tenant_audit_events (
                id,
                tenant_id,
                actor_profile_id,
                actor_type,
                actor_reference,
                action,
                target_type,
                target_id,
                target_reference,
                reason_code,
                correlation_id,
                outcome
            ) VALUES (?, ?, ?, 'TENANT_USER', NULL, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantAuditPublisher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public void publish(TenantAuditEvent event) {
        Objects.requireNonNull(event, "event is required");
        int inserted = jdbcTemplate.update(
                INSERT_AUDIT_EVENT,
                UUID.randomUUID(),
                event.scope().tenantId(),
                event.scope().profileId(),
                event.action().name(),
                event.targetType().name(),
                event.targetId().orElse(null),
                event.targetReference().orElse(null),
                event.reasonCode().orElse(null),
                event.correlationId().orElse(null),
                event.outcome().name());
        if (inserted != 1) {
            throw new IllegalStateException("Tenant audit event was not persisted");
        }
    }
}
