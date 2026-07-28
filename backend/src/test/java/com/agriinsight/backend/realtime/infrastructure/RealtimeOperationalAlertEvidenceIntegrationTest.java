package com.agriinsight.backend.realtime.infrastructure;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeAlertRecoveryTransition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCondition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOpenOperationalAlert;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore.ApplyResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies that database-backed DLT provenance is enforced under the worker role. */
@Testcontainers
class RealtimeOperationalAlertEvidenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2027-09-01T12:00:00Z");
    private static final UUID FORGED_EVENT_ID =
            UUID.fromString("79000000-0000-0000-0000-000000000001");
    private static final UUID DELIVERED_EVENT_ID =
            UUID.fromString("79000000-0000-0000-0000-000000000002");
    private static final UUID RACING_EVENT_ID =
            UUID.fromString("79000000-0000-0000-0000-000000000010");
    private static final Instant SOURCE_OCCURRED_AT = NOW.minus(Duration.ofMinutes(20));
    private static final Instant INITIAL_OBSERVED_AT = NOW.minus(Duration.ofMinutes(10));
    private static final Instant RESOLVED_AT = NOW.minus(Duration.ofMinutes(5));

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void rejectsASchemaValidDltRecordWithoutAMatchingOutboxSource() throws Exception {
        JdbcTemplate alertWorker = jdbcTemplate(
                PostgresIntegrationSupport.ALERT_WORKER,
                PostgresIntegrationSupport.ALERT_WORKER_PASSWORD);
        RealtimeOperationalAlertEvaluator evaluator = evaluator(alertWorker);

        evaluator.observeDeadLetter(deadLetterEvent(FORGED_EVENT_ID, NOW.plus(Duration.ofDays(1))));

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'REALTIME_DLT_RECORD'
                       AND source_event_id = '79000000-0000-0000-0000-000000000001'
                    """)).isZero();
        }
    }

    @Test
    void ignoresADelayedDltReplayForASourceAlreadyDelivered() throws Exception {
        seedDeliveredSource();
        JdbcTemplate alertWorker = jdbcTemplate(
                PostgresIntegrationSupport.ALERT_WORKER,
                PostgresIntegrationSupport.ALERT_WORKER_PASSWORD);
        TransactionTemplate alertWorkerTransaction = transaction(alertWorker);
        PostgresRealtimeOperationalEventSourceStore sourceStore =
                new PostgresRealtimeOperationalEventSourceStore(alertWorker);
        PostgresRealtimeOperationalAlertStore alertStore = new PostgresRealtimeOperationalAlertStore(alertWorker);
        RealtimeOperationalAlertCondition condition = new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                TENANT_A,
                DELIVERED_EVENT_ID,
                SOURCE_OCCURRED_AT);

        Optional<Instant> sourceOccurredAt = alertWorkerTransaction.execute(
                status -> sourceStore.findOccurredAt(TENANT_A, DELIVERED_EVENT_ID));
        assertThat(sourceOccurredAt).isEmpty();
        alertWorkerTransaction.executeWithoutResult(status -> {
            alertStore.upsert(condition, INITIAL_OBSERVED_AT);
            RealtimeOpenOperationalAlert alert = alertStore.findOpenAlerts(
                    RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, 10).getFirst();
            alertStore.recordClean(
                    alert,
                    new RealtimeAlertRecoveryTransition(RESOLVED_AT, 1, true),
                    RESOLVED_AT,
                    RESOLVED_AT);
        });

        evaluator(alertWorker).observeDeadLetter(deadLetterEvent(
                DELIVERED_EVENT_ID, NOW.plus(Duration.ofDays(1))));

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'REALTIME_DLT_RECORD'
                       AND source_event_id = '79000000-0000-0000-0000-000000000002'
                    """)).isEqualTo(1);
            assertThat(scalar(operator, """
                    SELECT state || ':' || version || ':' ||
                           to_char(last_observed_at, 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'REALTIME_DLT_RECORD'
                       AND source_event_id = '79000000-0000-0000-0000-000000000002'
                    """)).isEqualTo("RESOLVED:1:2027-09-01T11:50:00Z");
        }
    }

    @Test
    void waitsForAConcurrentReceiptAndDoesNotOpenAnAlertAfterDeliveryCommits() throws Exception {
        seedUndeliveredSourceForRace();
        JdbcTemplate realtime = jdbcTemplate(
                PostgresIntegrationSupport.REALTIME, PostgresIntegrationSupport.REALTIME_PASSWORD);
        JdbcTemplate alertWorker = jdbcTemplate(
                PostgresIntegrationSupport.ALERT_WORKER,
                PostgresIntegrationSupport.ALERT_WORKER_PASSWORD);
        CountDownLatch receiptRecorded = new CountDownLatch(1);
        CountDownLatch allowReceiptCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var receiptFuture = executor.submit(() -> transaction(realtime).executeWithoutResult(status -> {
                assertThat(new PostgresRealtimeReadModelStore(realtime).apply(racingSourceEvent()))
                        .isEqualTo(ApplyResult.APPLIED);
                receiptRecorded.countDown();
                awaitLatch(allowReceiptCommit, "receipt commit was not released");
            }));
            assertThat(receiptRecorded.await(5, TimeUnit.SECONDS)).isTrue();

            var observationFuture = executor.submit(() -> evaluator(alertWorker)
                    .observeDeadLetter(deadLetterEvent(
                            RACING_EVENT_ID, NOW.plus(Duration.ofDays(1)))));
            try {
                assertThat(alertWorkerIsWaitingForTheReceiptLock())
                        .as("DLT attribution must block on the receipt transaction")
                        .isTrue();
            } finally {
                allowReceiptCommit.countDown();
            }

            receiptFuture.get(5, TimeUnit.SECONDS);
            observationFuture.get(5, TimeUnit.SECONDS);
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM realtime_event_receipts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND event_id = '79000000-0000-0000-0000-000000000010'
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'REALTIME_DLT_RECORD'
                       AND source_event_id = '79000000-0000-0000-0000-000000000010'
                    """)).isZero();
        }
    }

    private static void seedDeliveredSource() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO api_command_records (
                        id, tenant_id, principal_id, http_method, route_template,
                        idempotency_key_digest, canonical_schema_version, command_hash, state)
                    VALUES
                        ('79000000-0000-0000-0000-000000000003',
                         '10000000-0000-0000-0000-000000000041',
                         '41000000-0000-0000-0000-000000000005', 'POST',
                         '/api/v1/realtime-dlt-replay', repeat('1', 64), 1, repeat('2', 64), 'IN_PROGRESS');
                    INSERT INTO outbox_events (
                        id, tenant_id, command_id, event_ordinal, aggregate_type,
                        aggregate_id, aggregate_version, event_type, schema_version,
                        occurred_at, payload, status, published_at)
                    VALUES
                        ('79000000-0000-0000-0000-000000000002',
                         '10000000-0000-0000-0000-000000000041',
                         '79000000-0000-0000-0000-000000000003', 0, 'FARM',
                         '79000000-0000-0000-0000-000000000004', 0,
                         'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                         TIMESTAMPTZ '2027-09-01T11:40:00Z', '{}'::jsonb,
                         'PUBLISHED', TIMESTAMPTZ '2027-09-01T11:41:00Z');
                    """);
        }
        JdbcTemplate realtime = jdbcTemplate(
                PostgresIntegrationSupport.REALTIME, PostgresIntegrationSupport.REALTIME_PASSWORD);
        transaction(realtime).executeWithoutResult(status -> realtime.update("""
                INSERT INTO realtime_event_receipts (
                    event_id, tenant_id, checksum, topic, partition_id, broker_offset)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                DELIVERED_EVENT_ID,
                TENANT_A,
                "b".repeat(64),
                "agriinsight.operational.v1",
                0,
                 707));
    }

    private static void seedUndeliveredSourceForRace() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO api_command_records (
                        id, tenant_id, principal_id, http_method, route_template,
                        idempotency_key_digest, canonical_schema_version, command_hash, state)
                    VALUES
                        ('79000000-0000-0000-0000-000000000011',
                         '10000000-0000-0000-0000-000000000041',
                         '41000000-0000-0000-0000-000000000005', 'POST',
                         '/api/v1/realtime-dlt-race', repeat('3', 64), 1, repeat('4', 64), 'IN_PROGRESS');
                    INSERT INTO outbox_events (
                        id, tenant_id, command_id, event_ordinal, aggregate_type,
                        aggregate_id, aggregate_version, event_type, schema_version,
                        occurred_at, payload, status, published_at)
                    VALUES
                        ('79000000-0000-0000-0000-000000000010',
                         '10000000-0000-0000-0000-000000000041',
                         '79000000-0000-0000-0000-000000000011', 0, 'FARM',
                         '71000000-0000-0000-0000-000000000001', 1,
                         'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                         TIMESTAMPTZ '2027-09-01T11:40:00Z', '{}'::jsonb,
                         'PUBLISHED', TIMESTAMPTZ '2027-09-01T11:41:00Z');
                    """);
        }
    }

    private static RealtimeOperationalEvent racingSourceEvent() {
        return new RealtimeOperationalEvent(
                RACING_EVENT_ID,
                TENANT_A,
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                SOURCE_OCCURRED_AT,
                "c".repeat(64),
                "agriinsight.operational.v1",
                0,
                708);
    }

    private static boolean alertWorkerIsWaitingForTheReceiptLock() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            do {
                if (count(operator, """
                        SELECT count(*)
                          FROM pg_catalog.pg_stat_activity
                         WHERE usename = 'agriinsight_alert_worker'
                           AND wait_event_type = 'Lock'
                           AND wait_event = 'advisory'
                        """) > 0) {
                    return true;
                }
                Thread.sleep(25);
            } while (Instant.now().isBefore(deadline));
        }
        return false;
    }

    private static void awaitLatch(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    private static RealtimeOperationalEvent deadLetterEvent(UUID eventId, Instant occurredAt) {
        return new RealtimeOperationalEvent(
                eventId,
                TENANT_A,
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                occurredAt,
                "a".repeat(64),
                "agriinsight.operational.v1.dlt",
                0,
                1);
    }

    private static RealtimeOperationalAlertEvaluator evaluator(JdbcTemplate alertWorker) {
        TransactionTemplate transaction = transaction(alertWorker);
        return new RealtimeOperationalAlertEvaluator(
                new PostgresRealtimeOperationalAlertStore(alertWorker),
                new PostgresRealtimeOperationalAlertScanStore(alertWorker),
                new PostgresRealtimeOperationalEventSourceStore(alertWorker),
                transaction,
                transaction,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new RealtimeAlertWorkerProperties(
                        true,
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(5),
                        2,
                        10,
                        Duration.ofSeconds(20),
                        "agriinsight-alert-observer-v1",
                        "agriinsight.operational.v1.alert-observer-failure",
                        2,
                        Duration.ofMillis(500)),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private static TransactionTemplate transaction(JdbcTemplate jdbcTemplate) {
        return new TransactionTemplate(new DataSourceTransactionManager(
                (DriverManagerDataSource) jdbcTemplate.getDataSource()));
    }

    private static JdbcTemplate jdbcTemplate(String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                PostgresIntegrationSupport.jdbcUrl(POSTGRESQL, "agriinsight"), username, password);
        return new JdbcTemplate(dataSource);
    }
}
