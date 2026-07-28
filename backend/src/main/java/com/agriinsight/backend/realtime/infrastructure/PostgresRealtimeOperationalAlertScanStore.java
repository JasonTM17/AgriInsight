package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertRecoveryCandidate;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanCursor;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanPage;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanProgress;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PostgreSQL reader and durable continuation state for fair worker condition scans. */
@Repository
@Profile("realtime-worker")
@ConditionalOnProperty(
        prefix = "agriinsight.realtime.alerts",
        name = "enabled",
        havingValue = "true")
public class PostgresRealtimeOperationalAlertScanStore implements RealtimeOperationalAlertScanStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresRealtimeOperationalAlertRecoveryCandidateReader recoveryCandidateReader;

    public PostgresRealtimeOperationalAlertScanStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.recoveryCandidateReader =
                new PostgresRealtimeOperationalAlertRecoveryCandidateReader(this.jdbcTemplate);
    }

    @Override
    public Optional<RealtimeOperationalAlertScanProgress> findProgress(
            RealtimeOperationalAlertPolicy policy) {
        requireTransaction();
        List<RealtimeOperationalAlertScanProgress> progress = jdbcTemplate.query(
                PostgresRealtimeOperationalAlertScanSql.FIND_SCAN_CURSOR,
                (result, rowNumber) -> new RealtimeOperationalAlertScanProgress(
                        mapCursor(
                                result.getObject("cursor_tenant_id", UUID.class),
                                timestampOrNull(result.getTimestamp("cursor_ordered_at")),
                                result.getObject("cursor_ordered_id", UUID.class)),
                        result.getTimestamp("cycle_started_at").toInstant()),
                Objects.requireNonNull(policy, "policy is required").name());
        return progress.stream().findFirst();
    }

    @Override
    public RealtimeOperationalAlertScanPage findPage(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Optional<RealtimeOperationalAlertScanCursor> cursor,
            int limit) {
        requireTransaction();
        RealtimeOperationalAlertPolicy requiredPolicy = Objects.requireNonNull(policy, "policy is required");
        Instant requiredThreshold = Objects.requireNonNull(threshold, "threshold is required");
        Optional<RealtimeOperationalAlertScanCursor> requiredCursor =
                Objects.requireNonNull(cursor, "cursor is required");
        if (limit < 2) {
            throw new IllegalArgumentException("limit must include a continuation probe");
        }
        return switch (requiredPolicy) {
            case OUTBOX_PUBLISH_BACKLOG -> findPublishBacklog(requiredThreshold, requiredCursor, limit);
            case REALTIME_DELIVERY_LAG -> findDeliveryLag(requiredThreshold, requiredCursor, limit);
            case REALTIME_DLT_RECORD -> findUnrecoveredDeadLetters(requiredCursor, limit);
        };
    }

    @Override
    public List<RealtimeOperationalAlertRecoveryCandidate> findRecoveryCandidates(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Instant staleBefore,
            int limit) {
        requireTransaction();
        return recoveryCandidateReader.findCandidates(policy, threshold, staleBefore, limit);
    }

    @Override
    public void saveProgress(
            RealtimeOperationalAlertPolicy policy,
            RealtimeOperationalAlertScanProgress progress,
            Instant updatedAt) {
        requireTransaction();
        RealtimeOperationalAlertPolicy requiredPolicy = Objects.requireNonNull(policy, "policy is required");
        RealtimeOperationalAlertScanProgress requiredProgress =
                Objects.requireNonNull(progress, "progress is required");
        RealtimeOperationalAlertScanCursor requiredCursor = requiredProgress.cursor();
        validateCursorKind(requiredPolicy, requiredCursor);
        Instant requiredUpdatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        Instant persistedUpdatedAt = requiredUpdatedAt.isBefore(requiredProgress.cycleStartedAt())
                ? requiredProgress.cycleStartedAt()
                : requiredUpdatedAt;
        int updated = jdbcTemplate.update(
                PostgresRealtimeOperationalAlertScanSql.UPSERT_SCAN_CURSOR,
                requiredPolicy.name(),
                requiredCursor.tenantId(),
                timestampOrNull(requiredCursor.orderedAt()),
                requiredCursor.orderedId(),
                Timestamp.from(requiredProgress.cycleStartedAt()),
                Timestamp.from(persistedUpdatedAt));
        if (updated != 1) {
            throw new IllegalStateException("scan cursor upsert did not report exactly one row");
        }
    }

    @Override
    public void clearProgress(RealtimeOperationalAlertPolicy policy) {
        requireTransaction();
        jdbcTemplate.update(
                PostgresRealtimeOperationalAlertScanSql.CLEAR_SCAN_CURSOR,
                Objects.requireNonNull(policy, "policy is required").name());
    }

    private RealtimeOperationalAlertScanPage findPublishBacklog(
            Instant threshold, Optional<RealtimeOperationalAlertScanCursor> cursor, int limit) {
        List<PostgresRealtimeOperationalAlertScanPageMapper.BacklogSourceRow> rows;
        if (cursor.isEmpty()) {
            rows = jdbcTemplate.query(
                    PostgresRealtimeOperationalAlertScanSql.FIND_PUBLISH_BACKLOG_FROM_START,
                    (result, rowNumber) -> PostgresRealtimeOperationalAlertScanPageMapper.mapBacklogSourceRow(result),
                    Timestamp.from(threshold),
                    limit);
            return PostgresRealtimeOperationalAlertScanPageMapper.backlogPage(rows, limit);
        }
        RealtimeOperationalAlertScanCursor requiredCursor = cursor.orElseThrow();
        validateCursorKind(RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, requiredCursor);
        rows = jdbcTemplate.query(
                PostgresRealtimeOperationalAlertScanSql.FIND_PUBLISH_BACKLOG_AFTER_TENANT,
                (result, rowNumber) -> PostgresRealtimeOperationalAlertScanPageMapper.mapBacklogSourceRow(result),
                Timestamp.from(threshold),
                requiredCursor.tenantId(),
                limit);
        return PostgresRealtimeOperationalAlertScanPageMapper.backlogPage(rows, limit);
    }

    private RealtimeOperationalAlertScanPage findDeliveryLag(
            Instant threshold, Optional<RealtimeOperationalAlertScanCursor> cursor, int limit) {
        if (cursor.isEmpty()) {
            return PostgresRealtimeOperationalAlertScanPageMapper.orderedSourcePage(jdbcTemplate.query(
                    PostgresRealtimeOperationalAlertScanSql.FIND_DELIVERY_LAG_FROM_START,
                    (result, rowNumber) -> PostgresRealtimeOperationalAlertScanPageMapper.mapOrderedSourceRow(
                            RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG, result),
                    Timestamp.from(threshold),
                    limit), limit);
        }
        RealtimeOperationalAlertScanCursor requiredCursor = cursor.orElseThrow();
        validateCursorKind(RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG, requiredCursor);
        return PostgresRealtimeOperationalAlertScanPageMapper.orderedSourcePage(jdbcTemplate.query(
                PostgresRealtimeOperationalAlertScanSql.FIND_DELIVERY_LAG_AFTER_CURSOR,
                (result, rowNumber) -> PostgresRealtimeOperationalAlertScanPageMapper.mapOrderedSourceRow(
                        RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG, result),
                Timestamp.from(threshold),
                Timestamp.from(requiredCursor.orderedAt()),
                requiredCursor.orderedId(),
                limit), limit);
    }

    private RealtimeOperationalAlertScanPage findUnrecoveredDeadLetters(
            Optional<RealtimeOperationalAlertScanCursor> cursor, int limit) {
        if (cursor.isEmpty()) {
            return PostgresRealtimeOperationalAlertScanPageMapper.orderedSourcePage(jdbcTemplate.query(
                    PostgresRealtimeOperationalAlertScanSql.FIND_UNRECOVERED_DLT_FROM_START,
                    (result, rowNumber) -> PostgresRealtimeOperationalAlertScanPageMapper.mapOrderedSourceRow(
                            RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, result),
                    limit), limit);
        }
        RealtimeOperationalAlertScanCursor requiredCursor = cursor.orElseThrow();
        validateCursorKind(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, requiredCursor);
        return PostgresRealtimeOperationalAlertScanPageMapper.orderedSourcePage(jdbcTemplate.query(
                PostgresRealtimeOperationalAlertScanSql.FIND_UNRECOVERED_DLT_AFTER_CURSOR,
                (result, rowNumber) -> PostgresRealtimeOperationalAlertScanPageMapper.mapOrderedSourceRow(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, result),
                Timestamp.from(requiredCursor.orderedAt()),
                requiredCursor.orderedId(),
                limit), limit);
    }

    private static RealtimeOperationalAlertScanCursor mapCursor(
            UUID tenantId, Instant orderedAt, UUID orderedId) {
        return tenantId == null
                ? RealtimeOperationalAlertScanCursor.ordered(orderedAt, orderedId)
                : RealtimeOperationalAlertScanCursor.tenant(tenantId);
    }

    private static void validateCursorKind(
            RealtimeOperationalAlertPolicy policy, RealtimeOperationalAlertScanCursor cursor) {
        boolean expectsTenant = policy == RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        if (cursor.isTenantCursor() != expectsTenant) {
            throw new IllegalArgumentException("scan cursor does not match " + policy.name());
        }
    }

    private static Instant timestampOrNull(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestampOrNull(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("operational alert scan store requires an active transaction");
        }
    }

}
