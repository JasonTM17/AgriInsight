package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.realtime.application.RealtimeMetric;
import com.agriinsight.backend.realtime.application.RealtimeSummary;
import com.agriinsight.backend.realtime.application.RealtimeSummaryStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL reader for bounded tenant metrics; raw Kafka values never reach this boundary. */
@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresRealtimeSummaryStore implements RealtimeSummaryStore {

    private static final int SUMMARY_LIMIT = 100;
    private static final String TOTALS_SQL = """
            SELECT COALESCE(SUM(event_count), 0)::bigint AS event_count,
                   MAX(last_occurred_at) AS last_occurred_at,
                   MAX(last_processed_at) AS last_processed_at,
                   COALESCE(GREATEST(
                       0::double precision,
                       EXTRACT(EPOCH FROM clock_timestamp() - MAX(last_processed_at)))::bigint, 0)
                       AS freshness_seconds
              FROM realtime_tenant_metrics
             WHERE tenant_id = ?
            """;
    private static final String METRICS_SQL = """
            SELECT event_type, aggregate_type, event_count, last_occurred_at, last_processed_at
              FROM realtime_tenant_metrics
             WHERE tenant_id = ?
             ORDER BY last_processed_at DESC, event_type ASC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresRealtimeSummaryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public RealtimeSummary summarize(ScopeContext scope) {
        ScopeContext required = requireTenantScope(scope);
        Totals totals = Objects.requireNonNull(jdbcTemplate.queryForObject(
                TOTALS_SQL,
                (result, rowNumber) -> new Totals(
                        result.getLong("event_count"),
                        optionalInstant(result.getTimestamp("last_occurred_at")),
                        optionalInstant(result.getTimestamp("last_processed_at")),
                        result.getLong("freshness_seconds")),
                required.tenantId()), "Realtime summary totals are required");
        List<RealtimeMetric> rows = jdbcTemplate.query(
                METRICS_SQL,
                (result, rowNumber) -> new RealtimeMetric(
                        result.getString("event_type"),
                        result.getString("aggregate_type"),
                        result.getLong("event_count"),
                        result.getTimestamp("last_occurred_at").toInstant(),
                        result.getTimestamp("last_processed_at").toInstant()),
                required.tenantId(), SUMMARY_LIMIT + 1);
        boolean hasMore = rows.size() > SUMMARY_LIMIT;
        return new RealtimeSummary(
                required.tenantId(), totals.eventCount(), totals.lastOccurredAt(),
                totals.lastProcessedAt(), totals.freshnessSeconds(),
                hasMore ? rows.subList(0, SUMMARY_LIMIT) : rows, SUMMARY_LIMIT, hasMore);
    }

    private static ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Realtime summaries require a tenant-wide scope");
        }
        return required;
    }

    private static Optional<Instant> optionalInstant(Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }

    private record Totals(
            long eventCount,
            Optional<Instant> lastOccurredAt,
            Optional<Instant> lastProcessedAt,
            long freshnessSeconds) {
    }
}
