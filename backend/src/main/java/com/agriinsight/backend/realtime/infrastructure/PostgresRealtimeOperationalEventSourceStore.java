package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalEventSourceStore;
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

/** Reads only worker-authorized outbox metadata while a DLT observation is being committed. */
@Repository
@Profile("realtime-worker")
@ConditionalOnProperty(
        prefix = "agriinsight.realtime.alerts",
        name = "enabled",
        havingValue = "true")
public class PostgresRealtimeOperationalEventSourceStore implements RealtimeOperationalEventSourceStore {

    private static final String FIND_SOURCE_OCCURRED_AT = """
            SELECT source.occurred_at
              FROM outbox_events source
             WHERE source.tenant_id = ?
               AND source.id = ?
               AND NOT EXISTS (
                   SELECT 1
                     FROM realtime_event_receipts receipt
                    WHERE receipt.tenant_id = source.tenant_id
                      AND receipt.event_id = source.id
               )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PostgresRealtimeEventReceiptLock receiptLock;

    public PostgresRealtimeOperationalEventSourceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.receiptLock = new PostgresRealtimeEventReceiptLock(this.jdbcTemplate);
    }

    @Override
    public Optional<Instant> findOccurredAt(UUID tenantId, UUID eventId) {
        requireTransaction();
        UUID requiredTenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        UUID requiredEventId = Objects.requireNonNull(eventId, "eventId is required");
        receiptLock.acquire(requiredEventId);
        List<Instant> occurredAt = jdbcTemplate.query(
                FIND_SOURCE_OCCURRED_AT,
                (result, rowNumber) -> result.getTimestamp("occurred_at").toInstant(),
                requiredTenantId,
                requiredEventId);
        return occurredAt.stream().findFirst();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("operational event source store requires an active transaction");
        }
    }
}
