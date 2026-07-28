package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.ALERT_WORKER;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.ALERT_WORKER_PASSWORD;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanCursor;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanPage;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanProgress;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertScanStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RealtimeOperationalAlertScanStoreIntegrationTest {

    private static final Instant OBSERVED_AT = Instant.parse("2027-09-01T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void continuesBoundedDltPagesWithLimitIncludingAContinuationProbe() throws Exception {
        seedUnrecoveredDeadLetters();
        JdbcTemplate alertWorker = jdbcTemplate(ALERT_WORKER, ALERT_WORKER_PASSWORD);
        PostgresRealtimeOperationalAlertScanStore store =
                new PostgresRealtimeOperationalAlertScanStore(alertWorker);
        TransactionTemplate transaction = transaction(alertWorker);

        RealtimeOperationalAlertScanPage first = transaction.execute(status -> store.findPage(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, OBSERVED_AT, Optional.empty(), 2));
        assertThat(first.candidates())
                .extracting(candidate -> candidate.condition().sourceEventId())
                .containsExactly(UUID.fromString("77000000-0000-0000-0000-000000000001"));
        assertThat(first.hasMore()).isTrue();
        assertThat(first.continuationCursor()).contains(RealtimeOperationalAlertScanCursor.ordered(
                OBSERVED_AT.plusSeconds(1), UUID.fromString("66000000-0000-0000-0000-000000000001")));
        RealtimeOperationalAlertScanProgress progress = new RealtimeOperationalAlertScanProgress(
                first.continuationCursor().orElseThrow(), OBSERVED_AT);
        transaction.executeWithoutResult(status -> store.saveProgress(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                progress,
                OBSERVED_AT.plusSeconds(10)));

        RealtimeOperationalAlertScanProgress stored = transaction.execute(status -> store
                .findProgress(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD)
                .orElseThrow());
        assertThat(stored).isEqualTo(progress);

        RealtimeOperationalAlertScanPage second = transaction.execute(status -> store.findPage(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                OBSERVED_AT,
                Optional.of(stored.cursor()),
                2));
        assertThat(second.candidates())
                .extracting(candidate -> candidate.condition().sourceEventId())
                .containsExactly(UUID.fromString("77000000-0000-0000-0000-000000000002"));
        assertThat(second.hasMore()).isTrue();
        assertThat(second.continuationCursor()).contains(RealtimeOperationalAlertScanCursor.ordered(
                OBSERVED_AT.plusSeconds(2), UUID.fromString("66000000-0000-0000-0000-000000000002")));
        RealtimeOperationalAlertScanProgress nextProgress = new RealtimeOperationalAlertScanProgress(
                second.continuationCursor().orElseThrow(), stored.cycleStartedAt());
        transaction.executeWithoutResult(status -> store.saveProgress(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                nextProgress,
                OBSERVED_AT.plusSeconds(20)));

        RealtimeOperationalAlertScanPage tail = transaction.execute(status -> store.findPage(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                OBSERVED_AT,
                Optional.of(nextProgress.cursor()),
                2));
        assertThat(tail.candidates())
                .extracting(candidate -> candidate.condition().sourceEventId())
                .containsExactly(UUID.fromString("77000000-0000-0000-0000-000000000003"));
        assertThat(tail.hasMore()).isFalse();
        assertThat(tail.continuationCursor()).isEmpty();

        transaction.executeWithoutResult(
                status -> store.clearProgress(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD));
        Optional<RealtimeOperationalAlertScanProgress> clearedProgress = transaction.execute(
                status -> store.findProgress(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD));
        assertThat(clearedProgress).isEmpty();
    }

    @Test
    void advancesAcrossReceiptedDeliveryRowsToReachASparseUnreceivedTail() throws Exception {
        seedDeliveryRows();
        JdbcTemplate alertWorker = jdbcTemplate(ALERT_WORKER, ALERT_WORKER_PASSWORD);
        PostgresRealtimeOperationalAlertScanStore store =
                new PostgresRealtimeOperationalAlertScanStore(alertWorker);
        TransactionTemplate transaction = transaction(alertWorker);
        Instant threshold = OBSERVED_AT.plusSeconds(10);

        RealtimeOperationalAlertScanPage first = transaction.execute(status -> store.findPage(
                RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG, threshold, Optional.empty(), 2));
        assertThat(first.candidates()).isEmpty();
        assertThat(first.hasMore()).isTrue();
        assertThat(first.continuationCursor()).contains(RealtimeOperationalAlertScanCursor.ordered(
                OBSERVED_AT.plusSeconds(1), UUID.fromString("78000000-0000-0000-0000-000000000020")));

        RealtimeOperationalAlertScanPage second = transaction.execute(status -> store.findPage(
                RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG,
                threshold,
                first.continuationCursor(),
                2));
        assertThat(second.candidates()).isEmpty();
        assertThat(second.hasMore()).isTrue();
        assertThat(second.continuationCursor()).contains(RealtimeOperationalAlertScanCursor.ordered(
                OBSERVED_AT.plusSeconds(2), UUID.fromString("78000000-0000-0000-0000-000000000021")));

        RealtimeOperationalAlertScanPage tail = transaction.execute(status -> store.findPage(
                RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG,
                threshold,
                second.continuationCursor(),
                2));
        assertThat(tail.candidates())
                .extracting(candidate -> candidate.condition().sourceEventId())
                .containsExactly(UUID.fromString("78000000-0000-0000-0000-000000000022"));
        assertThat(tail.hasMore()).isFalse();
    }

    private static void seedUnrecoveredDeadLetters() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO realtime_operational_alerts (
                        id, tenant_id, policy_code, dedupe_key, severity, state,
                        source_event_id, source_occurred_at, opened_at, last_observed_at,
                        last_evaluated_at)
                    SELECT ('66000000-0000-0000-0000-' || lpad(series::text, 12, '0'))::uuid,
                           '10000000-0000-0000-0000-000000000041'::uuid,
                           'REALTIME_DLT_RECORD', lpad(to_hex(series), 64, '0'), 'CRITICAL', 'OPEN',
                           ('77000000-0000-0000-0000-' || lpad(series::text, 12, '0'))::uuid,
                           TIMESTAMPTZ '2027-09-01T00:00:00Z' + make_interval(secs => series),
                           TIMESTAMPTZ '2027-09-01T00:00:00Z',
                           TIMESTAMPTZ '2027-09-01T00:00:00Z' + make_interval(secs => series),
                           TIMESTAMPTZ '2027-09-01T00:00:00Z'
                      FROM generate_series(1, 3) AS series
                    """);
        }
    }

    private static void seedDeliveryRows() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO api_command_records (
                        id, tenant_id, principal_id, http_method, route_template,
                        idempotency_key_digest, canonical_schema_version, command_hash, state)
                    VALUES
                        ('78000000-0000-0000-0000-000000000010',
                         '10000000-0000-0000-0000-000000000041',
                         '41000000-0000-0000-0000-000000000005', 'POST',
                         '/api/v1/realtime-delivery-1', repeat('1', 64), 1, repeat('a', 64), 'IN_PROGRESS'),
                        ('78000000-0000-0000-0000-000000000011',
                         '10000000-0000-0000-0000-000000000041',
                         '41000000-0000-0000-0000-000000000005', 'POST',
                         '/api/v1/realtime-delivery-2', repeat('2', 64), 1, repeat('b', 64), 'IN_PROGRESS'),
                        ('78000000-0000-0000-0000-000000000012',
                         '10000000-0000-0000-0000-000000000041',
                         '41000000-0000-0000-0000-000000000005', 'POST',
                         '/api/v1/realtime-delivery-3', repeat('3', 64), 1, repeat('c', 64), 'IN_PROGRESS');
                    INSERT INTO outbox_events (
                        id, tenant_id, command_id, event_ordinal, aggregate_type,
                        aggregate_id, aggregate_version, event_type, schema_version,
                        occurred_at, payload, status, published_at)
                    VALUES
                        ('78000000-0000-0000-0000-000000000020',
                         '10000000-0000-0000-0000-000000000041',
                         '78000000-0000-0000-0000-000000000010', 0, 'FARM',
                         '78000000-0000-0000-0000-000000000030', 0,
                         'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                         TIMESTAMPTZ '2027-09-01T00:00:00Z', '{}'::jsonb,
                         'PUBLISHED', TIMESTAMPTZ '2027-09-01T00:00:01Z'),
                        ('78000000-0000-0000-0000-000000000021',
                         '10000000-0000-0000-0000-000000000041',
                         '78000000-0000-0000-0000-000000000011', 0, 'FARM',
                         '78000000-0000-0000-0000-000000000031', 0,
                         'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                         TIMESTAMPTZ '2027-09-01T00:00:00Z', '{}'::jsonb,
                         'PUBLISHED', TIMESTAMPTZ '2027-09-01T00:00:02Z'),
                        ('78000000-0000-0000-0000-000000000022',
                         '10000000-0000-0000-0000-000000000041',
                         '78000000-0000-0000-0000-000000000012', 0, 'FARM',
                         '78000000-0000-0000-0000-000000000032', 0,
                         'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                         TIMESTAMPTZ '2027-09-01T00:00:00Z', '{}'::jsonb,
                         'PUBLISHED', TIMESTAMPTZ '2027-09-01T00:00:03Z');
                    INSERT INTO realtime_event_receipts (
                        event_id, tenant_id, checksum, topic, partition_id, broker_offset)
                    VALUES
                        ('78000000-0000-0000-0000-000000000020',
                         '10000000-0000-0000-0000-000000000041', repeat('d', 64),
                         'agriinsight.operational.v1', 0, 1),
                        ('78000000-0000-0000-0000-000000000021',
                         '10000000-0000-0000-0000-000000000041', repeat('e', 64),
                         'agriinsight.operational.v1', 0, 2);
                    """);
        }
    }

    private static JdbcTemplate jdbcTemplate(String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                PostgresIntegrationSupport.jdbcUrl(POSTGRESQL, "agriinsight"), username, password);
        return new JdbcTemplate(dataSource);
    }

    private static TransactionTemplate transaction(JdbcTemplate jdbcTemplate) {
        return new TransactionTemplate(new DataSourceTransactionManager(
                (DriverManagerDataSource) jdbcTemplate.getDataSource()));
    }
}
