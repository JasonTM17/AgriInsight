package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeEventConflictException;
import com.agriinsight.backend.realtime.application.RealtimeEventOrderingException;
import com.agriinsight.backend.realtime.application.RealtimeEventOrderingException.Reason;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PostgreSQL receipt, aggregate-order, and bounded metric projection for one event transaction. */
@Repository
@Profile("!test")
@ConditionalOnProperty(
        prefix = "agriinsight.realtime",
        name = "consumer-enabled",
        havingValue = "true")
public class PostgresRealtimeReadModelStore implements RealtimeReadModelStore {

    private static final String INSERT_RECEIPT = """
            INSERT INTO realtime_event_receipts (
                event_id, tenant_id, checksum, topic, partition_id, broker_offset)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;
    private static final String FIND_RECEIPT = """
            SELECT tenant_id, checksum
              FROM realtime_event_receipts
             WHERE event_id = ?
            """;
    private static final String INSERT_PROGRESS = """
            INSERT INTO realtime_aggregate_progress (
                tenant_id, aggregate_type, aggregate_id, last_version, last_event_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, aggregate_type, aggregate_id) DO NOTHING
            """;
    private static final String LOCK_PROGRESS = """
            SELECT last_version
              FROM realtime_aggregate_progress
             WHERE tenant_id = ? AND aggregate_type = ? AND aggregate_id = ?
             FOR UPDATE
            """;
    private static final String UPDATE_PROGRESS = """
            UPDATE realtime_aggregate_progress
               SET last_version = ?, last_event_id = ?, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = ? AND aggregate_type = ? AND aggregate_id = ?
               AND last_version = ?
            """;
    private static final String UPSERT_METRIC = """
            INSERT INTO realtime_tenant_metrics (
                tenant_id, event_type, aggregate_type, event_count,
                last_occurred_at, last_processed_at)
            VALUES (?, ?, ?, 1, ?, clock_timestamp())
            ON CONFLICT (tenant_id, event_type) DO UPDATE
               SET event_count = realtime_tenant_metrics.event_count + 1,
                   last_occurred_at = GREATEST(
                        realtime_tenant_metrics.last_occurred_at,
                        EXCLUDED.last_occurred_at),
                   last_processed_at = GREATEST(
                        realtime_tenant_metrics.last_processed_at,
                        clock_timestamp())
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresRealtimeReadModelStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public ApplyResult apply(RealtimeOperationalEvent event) {
        RealtimeOperationalEvent required = Objects.requireNonNull(event, "event is required");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("realtime read model requires an active transaction");
        }
        if (recordReceipt(required) == ReceiptResult.DUPLICATE) {
            return ApplyResult.DUPLICATE;
        }
        advanceAggregateProgress(required);
        incrementMetric(required);
        return ApplyResult.APPLIED;
    }

    private ReceiptResult recordReceipt(RealtimeOperationalEvent event) {
        int inserted;
        try {
            inserted = jdbcTemplate.update(
                    INSERT_RECEIPT,
                    event.eventId(),
                    event.tenantId(),
                    event.checksum(),
                    event.topic(),
                    event.partition(),
                    event.offset());
        } catch (DuplicateKeyException exception) {
            throw new RealtimeEventConflictException(
                    "Kafka broker coordinate belongs to another event", exception);
        }
        if (inserted == 1) {
            return ReceiptResult.INSERTED;
        }
        if (inserted != 0) {
            throw new IllegalStateException("receipt insert did not report a valid row count");
        }

        List<StoredReceipt> receipts = jdbcTemplate.query(
                FIND_RECEIPT,
                (result, rowNumber) -> new StoredReceipt(
                        result.getObject("tenant_id", UUID.class), result.getString("checksum")),
                event.eventId());
        if (receipts.size() != 1) {
            throw new IllegalStateException("existing receipt could not be resolved");
        }
        StoredReceipt receipt = receipts.getFirst();
        if (!event.tenantId().equals(receipt.tenantId())
                || !event.checksum().equals(receipt.checksum())) {
            throw new RealtimeEventConflictException(
                    "event id cannot be reused with different event content");
        }
        return ReceiptResult.DUPLICATE;
    }

    private void advanceAggregateProgress(RealtimeOperationalEvent event) {
        int baseline = jdbcTemplate.update(
                INSERT_PROGRESS,
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.aggregateVersion(),
                event.eventId());
        if (baseline == 1) {
            return;
        }
        if (baseline != 0) {
            throw new IllegalStateException("aggregate progress insert did not report a valid row count");
        }

        List<Long> versions = jdbcTemplate.query(
                LOCK_PROGRESS,
                (result, rowNumber) -> result.getLong("last_version"),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId());
        if (versions.size() != 1) {
            throw new IllegalStateException("aggregate progress could not be resolved");
        }
        long lastVersion = versions.getFirst();
        if (event.aggregateVersion() <= lastVersion) {
            throw new RealtimeEventOrderingException(Reason.STALE);
        }
        if (lastVersion == Long.MAX_VALUE || event.aggregateVersion() != lastVersion + 1) {
            throw new RealtimeEventOrderingException(Reason.GAP);
        }
        int updated = jdbcTemplate.update(
                UPDATE_PROGRESS,
                event.aggregateVersion(),
                event.eventId(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                lastVersion);
        if (updated != 1) {
            throw new IllegalStateException("aggregate progress did not advance exactly once");
        }
    }

    private void incrementMetric(RealtimeOperationalEvent event) {
        int updated = jdbcTemplate.update(
                UPSERT_METRIC,
                event.tenantId(),
                event.eventType(),
                event.aggregateType(),
                Timestamp.from(event.occurredAt()));
        if (updated != 1) {
            throw new IllegalStateException("tenant metric did not advance exactly once");
        }
    }

    private enum ReceiptResult {
        INSERTED,
        DUPLICATE
    }

    private record StoredReceipt(UUID tenantId, String checksum) {
    }
}
