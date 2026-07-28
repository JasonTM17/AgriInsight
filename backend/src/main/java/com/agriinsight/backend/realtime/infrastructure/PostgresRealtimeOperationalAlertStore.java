package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeAlertRecoveryTransition;
import com.agriinsight.backend.realtime.application.RealtimeOpenOperationalAlert;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCondition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PostgreSQL projection for bounded metadata-only operational alert evaluation. */
@Repository
@Profile("realtime-worker")
@ConditionalOnProperty(
        prefix = "agriinsight.realtime.alerts",
        name = "enabled",
        havingValue = "true")
public class PostgresRealtimeOperationalAlertStore implements RealtimeOperationalAlertStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRealtimeOperationalAlertStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public boolean tryAcquirePolicyLock(RealtimeOperationalAlertPolicy policy) {
        requireTransaction();
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext(?))",
                Boolean.class,
                Objects.requireNonNull(policy, "policy is required").name());
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public List<RealtimeOperationalAlertCondition> findConditions(
            RealtimeOperationalAlertPolicy policy, Instant threshold, int limit) {
        requireTransaction();
        RealtimeOperationalAlertPolicy required = Objects.requireNonNull(policy, "policy is required");
        Instant requiredThreshold = Objects.requireNonNull(threshold, "threshold is required");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return switch (required) {
            case OUTBOX_PUBLISH_BACKLOG -> jdbcTemplate.query(
                    PostgresRealtimeOperationalAlertSql.FIND_PUBLISH_BACKLOG,
                    (result, rowNumber) -> new RealtimeOperationalAlertCondition(
                            required,
                            result.getObject("tenant_id", UUID.class),
                            null,
                            result.getTimestamp("source_occurred_at").toInstant()),
                    Timestamp.from(requiredThreshold),
                    limit);
            case REALTIME_DELIVERY_LAG -> jdbcTemplate.query(
                    PostgresRealtimeOperationalAlertSql.FIND_DELIVERY_LAG,
                    (result, rowNumber) -> new RealtimeOperationalAlertCondition(
                            required,
                            result.getObject("tenant_id", UUID.class),
                            result.getObject("source_event_id", UUID.class),
                            result.getTimestamp("source_occurred_at").toInstant()),
                    Timestamp.from(requiredThreshold),
                    limit);
            case REALTIME_DLT_RECORD -> jdbcTemplate.query(
                    PostgresRealtimeOperationalAlertSql.FIND_UNRECOVERED_DLT,
                    (result, rowNumber) -> new RealtimeOperationalAlertCondition(
                            required,
                            result.getObject("tenant_id", UUID.class),
                            result.getObject("source_event_id", UUID.class),
                            result.getTimestamp("source_occurred_at").toInstant()),
                    limit);
        };
    }

    @Override
    public List<RealtimeOpenOperationalAlert> findOpenAlerts(
            RealtimeOperationalAlertPolicy policy, int limit) {
        requireTransaction();
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query(
                PostgresRealtimeOperationalAlertSql.FIND_OPEN_ALERTS,
                (result, rowNumber) -> new RealtimeOpenOperationalAlert(
                        result.getObject("id", UUID.class),
                        result.getString("dedupe_key"),
                        timestampOrNull(result.getTimestamp("clean_since")),
                        result.getInt("clean_scan_count")),
                Objects.requireNonNull(policy, "policy is required").name(),
                limit);
    }

    @Override
    public void upsert(RealtimeOperationalAlertCondition condition, Instant observedAt) {
        requireTransaction();
        RealtimeOperationalAlertCondition required =
                Objects.requireNonNull(condition, "condition is required");
        Timestamp observed = Timestamp.from(Objects.requireNonNull(observedAt, "observedAt is required"));
        int updated = jdbcTemplate.update(
                PostgresRealtimeOperationalAlertSql.UPSERT_ALERT,
                UUID.randomUUID(),
                required.tenantId(),
                required.policy().name(),
                required.dedupeKey(),
                required.policy().severity().name(),
                required.sourceEventId(),
                Timestamp.from(required.sourceOccurredAt()),
                observed,
                observed,
                observed);
        if (updated != 1) {
            throw new IllegalStateException("alert upsert did not report exactly one row");
        }
    }

    @Override
    public void recordClean(
            RealtimeOpenOperationalAlert alert,
            RealtimeAlertRecoveryTransition transition,
            Instant evaluatedAt) {
        requireTransaction();
        RealtimeOpenOperationalAlert requiredAlert = Objects.requireNonNull(alert, "alert is required");
        RealtimeAlertRecoveryTransition requiredTransition =
                Objects.requireNonNull(transition, "transition is required");
        Timestamp evaluated = Timestamp.from(Objects.requireNonNull(evaluatedAt, "evaluatedAt is required"));
        int updated = jdbcTemplate.update(
                PostgresRealtimeOperationalAlertSql.RECORD_CLEAN,
                Timestamp.from(requiredTransition.cleanSince()),
                requiredTransition.cleanScanCount(),
                requiredTransition.resolve(),
                requiredTransition.resolve(),
                requiredTransition.resolve() ? evaluated : null,
                evaluated,
                requiredAlert.id());
        if (updated != 1) {
            throw new IllegalStateException("open alert recovery update did not report exactly one row");
        }
    }

    private static Instant timestampOrNull(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("operational alert store requires an active transaction");
        }
    }
}
