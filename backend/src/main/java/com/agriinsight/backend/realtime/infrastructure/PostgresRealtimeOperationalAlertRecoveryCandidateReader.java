package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertRecoveryCandidate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads a bounded stale-alert page and verifies its conditions in the same PostgreSQL snapshot. */
final class PostgresRealtimeOperationalAlertRecoveryCandidateReader {

    private final JdbcTemplate jdbcTemplate;

    PostgresRealtimeOperationalAlertRecoveryCandidateReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    List<RealtimeOperationalAlertRecoveryCandidate> findCandidates(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Instant staleBefore,
            int limit) {
        RealtimeOperationalAlertPolicy requiredPolicy = Objects.requireNonNull(policy, "policy is required");
        Instant requiredThreshold = Objects.requireNonNull(threshold, "threshold is required");
        Instant requiredStaleBefore = Objects.requireNonNull(staleBefore, "staleBefore is required");
        if (limit < 2) {
            throw new IllegalArgumentException("recovery limit must include a saturation probe");
        }
        return switch (requiredPolicy) {
            case OUTBOX_PUBLISH_BACKLOG -> query(
                    PostgresRealtimeOperationalAlertRecoverySql.FIND_PUBLISH_BACKLOG_CANDIDATES,
                    requiredPolicy,
                    Timestamp.from(requiredStaleBefore),
                    limit,
                    Timestamp.from(requiredThreshold));
            case REALTIME_DELIVERY_LAG -> query(
                    PostgresRealtimeOperationalAlertRecoverySql.FIND_DELIVERY_LAG_CANDIDATES,
                    requiredPolicy,
                    Timestamp.from(requiredStaleBefore),
                    limit,
                    Timestamp.from(requiredThreshold));
            case REALTIME_DLT_RECORD -> query(
                    PostgresRealtimeOperationalAlertRecoverySql.FIND_DLT_CANDIDATES,
                    requiredPolicy,
                    Timestamp.from(requiredStaleBefore),
                    limit);
        };
    }

    private List<RealtimeOperationalAlertRecoveryCandidate> query(
            String sql, RealtimeOperationalAlertPolicy policy, Object... parameters) {
        return jdbcTemplate.query(
                sql,
                (result, rowNumber) ->
                        PostgresRealtimeOperationalAlertScanPageMapper.mapRecoveryCandidate(policy, result),
                parameters);
    }
}
