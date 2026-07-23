package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPage;
import com.agriinsight.backend.authorization.application.TenantAuditQuery;
import com.agriinsight.backend.authorization.application.TenantAuditReadStore;
import com.agriinsight.backend.authorization.application.TenantAuditRecord;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresTenantAuditReadStore implements TenantAuditReadStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantAuditReadStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public TenantAuditPage findAll(ScopeContext scope, TenantAuditQuery query) {
        requireTenantScope(scope);
        Objects.requireNonNull(query, "query is required");
        List<TenantAuditRecord> rows = jdbcTemplate.query("""
                SELECT event_id, event_actor_type, event_actor_profile_id, event_action,
                       event_target_type, event_target_id, event_reason_code,
                       event_correlation_id, event_outcome, event_occurred_at
                  FROM agriinsight_security.list_tenant_audit_events(
                       ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (result, rowNumber) -> new TenantAuditRecord(
                        result.getObject("event_id", UUID.class),
                        TenantAuditRecord.ActorType.valueOf(result.getString("event_actor_type")),
                        Optional.ofNullable(result.getObject("event_actor_profile_id", UUID.class)),
                        result.getString("event_action"),
                        result.getString("event_target_type"),
                        Optional.ofNullable(result.getObject("event_target_id", UUID.class)),
                        Optional.ofNullable(result.getString("event_reason_code")),
                        Optional.ofNullable(result.getString("event_correlation_id")),
                        TenantAuditEvent.Outcome.valueOf(result.getString("event_outcome")),
                        result.getTimestamp("event_occurred_at").toInstant()),
                scope.tenantId(),
                query.actorProfileId().orElse(null),
                query.action().orElse(null),
                query.targetType().orElse(null),
                query.targetId().orElse(null),
                query.outcome().map(Enum::name).orElse(null),
                query.limit() + 1,
                query.offset());
        boolean hasMore = rows.size() > query.limit();
        List<TenantAuditRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new TenantAuditPage(items, query.limit(), query.offset(), hasMore);
    }

    private void requireTenantScope(ScopeContext scope) {
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Audit event store requires tenant-wide scope");
        }
    }
}
