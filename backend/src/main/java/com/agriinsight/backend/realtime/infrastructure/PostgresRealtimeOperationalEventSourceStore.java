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
            SELECT occurred_at
              FROM outbox_events
             WHERE tenant_id = ?
               AND id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresRealtimeOperationalEventSourceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public Optional<Instant> findOccurredAt(UUID tenantId, UUID eventId) {
        requireTransaction();
        List<Instant> occurredAt = jdbcTemplate.query(
                FIND_SOURCE_OCCURRED_AT,
                (result, rowNumber) -> result.getTimestamp("occurred_at").toInstant(),
                Objects.requireNonNull(tenantId, "tenantId is required"),
                Objects.requireNonNull(eventId, "eventId is required"));
        return occurredAt.stream().findFirst();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("operational event source store requires an active transaction");
        }
    }
}
