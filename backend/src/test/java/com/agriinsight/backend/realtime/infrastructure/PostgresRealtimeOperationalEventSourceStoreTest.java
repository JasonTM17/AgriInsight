package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostgresRealtimeOperationalEventSourceStoreTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID EVENT_ID =
            UUID.fromString("79000000-0000-0000-0000-000000000010");
    private static final Instant OCCURRED_AT = Instant.parse("2027-09-01T11:40:00Z");

    @BeforeEach
    void activateTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void acquiresTheReceiptGuardBeforeCheckingUndeliveredSourceEvidence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                contains("FROM outbox_events source"),
                org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(),
                any(Object[].class)))
                .thenReturn(List.of(OCCURRED_AT));

        assertThat(new PostgresRealtimeOperationalEventSourceStore(jdbcTemplate)
                        .findOccurredAt(TENANT_ID, EVENT_ID))
                .contains(OCCURRED_AT);

        InOrder ordering = inOrder(jdbcTemplate);
        ordering.verify(jdbcTemplate).queryForObject(
                contains("pg_advisory_xact_lock"),
                eq(Object.class),
                eq(EVENT_ID.toString()));
        ordering.verify(jdbcTemplate).query(
                contains("FROM outbox_events source"),
                org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(),
                any(Object[].class));
    }

    @Test
    void failsClosedOutsideAnActiveTransactionBeforeTakingALock() {
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThatThrownBy(() -> new PostgresRealtimeOperationalEventSourceStore(
                                mock(JdbcTemplate.class))
                        .findOccurredAt(TENANT_ID, EVENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational event source store requires an active transaction");
    }
}
