package com.agriinsight.backend.realtime.infrastructure;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes receipt recording and DLT attribution for one event until their transaction completes. */
final class PostgresRealtimeEventReceiptLock {

    static final String ACQUIRE_EVENT_LOCK = """
            SELECT pg_advisory_xact_lock(
                hashtextextended('agriinsight:realtime-event-receipt:' || ?::text, 0)
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresRealtimeEventReceiptLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    void acquire(UUID eventId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("realtime event receipt lock requires an active transaction");
        }
        jdbcTemplate.queryForObject(
                ACQUIRE_EVENT_LOCK,
                Object.class,
                Objects.requireNonNull(eventId, "eventId is required").toString());
    }
}
