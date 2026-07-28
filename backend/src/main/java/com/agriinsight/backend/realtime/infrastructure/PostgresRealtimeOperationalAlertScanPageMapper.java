package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCandidate;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCondition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertRecoveryCandidate;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanCursor;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanPage;
import com.agriinsight.backend.realtime.application.RealtimeOpenOperationalAlert;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Maps bounded PostgreSQL result pages to alert candidates and durable continuations. */
final class PostgresRealtimeOperationalAlertScanPageMapper {

    private PostgresRealtimeOperationalAlertScanPageMapper() {
    }

    static BacklogSourceRow mapBacklogSourceRow(ResultSet result) throws SQLException {
        return new BacklogSourceRow(
                result.getObject("tenant_id", UUID.class),
                timestampOrNull(result.getTimestamp("source_occurred_at")));
    }

    static OrderedSourceRow mapOrderedSourceRow(
            RealtimeOperationalAlertPolicy policy, ResultSet result) throws SQLException {
        return new OrderedSourceRow(
                policy,
                result.getObject("tenant_id", UUID.class),
                result.getObject("source_event_id", UUID.class),
                result.getTimestamp("source_occurred_at").toInstant(),
                RealtimeOperationalAlertScanCursor.ordered(
                        result.getTimestamp("cursor_ordered_at").toInstant(),
                        result.getObject("cursor_ordered_id", UUID.class)),
                result.getObject("receipt_event_id", UUID.class) != null);
    }

    static RealtimeOperationalAlertScanPage backlogPage(List<BacklogSourceRow> rows, int limit) {
        boolean hasMore = rows.size() == limit;
        List<BacklogSourceRow> inspectedRows = hasMore ? rows.subList(0, limit - 1) : rows;
        List<RealtimeOperationalAlertCandidate> candidates = inspectedRows.stream()
                .filter(row -> row.sourceOccurredAt() != null)
                .map(row -> new RealtimeOperationalAlertCandidate(
                        new RealtimeOperationalAlertCondition(
                                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                                row.tenantId(),
                                null,
                                row.sourceOccurredAt()),
                        RealtimeOperationalAlertScanCursor.tenant(row.tenantId())))
                .toList();
        Optional<RealtimeOperationalAlertScanCursor> continuation = hasMore
                ? Optional.of(RealtimeOperationalAlertScanCursor.tenant(
                        inspectedRows.getLast().tenantId()))
                : Optional.empty();
        return new RealtimeOperationalAlertScanPage(candidates, continuation, hasMore);
    }

    static RealtimeOperationalAlertScanPage orderedSourcePage(
            List<OrderedSourceRow> rows, int limit) {
        boolean hasMore = rows.size() == limit;
        List<OrderedSourceRow> inspectedRows = hasMore ? rows.subList(0, limit - 1) : rows;
        List<RealtimeOperationalAlertCandidate> candidates = inspectedRows.stream()
                .filter(row -> !row.hasReceipt())
                .map(row -> new RealtimeOperationalAlertCandidate(
                        new RealtimeOperationalAlertCondition(
                                row.policy(),
                                row.tenantId(),
                                row.sourceEventId(),
                                row.sourceOccurredAt()),
                        row.scanCursor()))
                .toList();
        Optional<RealtimeOperationalAlertScanCursor> continuation = hasMore
                ? Optional.of(inspectedRows.getLast().scanCursor())
                : Optional.empty();
        return new RealtimeOperationalAlertScanPage(candidates, continuation, hasMore);
    }

    static RealtimeOperationalAlertRecoveryCandidate mapRecoveryCandidate(
            RealtimeOperationalAlertPolicy policy, ResultSet result) throws SQLException {
        RealtimeOpenOperationalAlert alert = new RealtimeOpenOperationalAlert(
                result.getObject("alert_id", UUID.class),
                result.getString("dedupe_key"),
                timestampOrNull(result.getTimestamp("clean_since")),
                result.getInt("clean_scan_count"));
        UUID tenantId = result.getObject("condition_tenant_id", UUID.class);
        if (tenantId == null) {
            return new RealtimeOperationalAlertRecoveryCandidate(alert, Optional.empty());
        }
        RealtimeOperationalAlertCondition condition = new RealtimeOperationalAlertCondition(
                policy,
                tenantId,
                result.getObject("condition_source_event_id", UUID.class),
                result.getTimestamp("condition_source_occurred_at").toInstant());
        return new RealtimeOperationalAlertRecoveryCandidate(alert, Optional.of(condition));
    }

    private static Instant timestampOrNull(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    record BacklogSourceRow(UUID tenantId, Instant sourceOccurredAt) {
    }

    record OrderedSourceRow(
            RealtimeOperationalAlertPolicy policy,
            UUID tenantId,
            UUID sourceEventId,
            Instant sourceOccurredAt,
            RealtimeOperationalAlertScanCursor scanCursor,
            boolean hasReceipt) {
    }
}
