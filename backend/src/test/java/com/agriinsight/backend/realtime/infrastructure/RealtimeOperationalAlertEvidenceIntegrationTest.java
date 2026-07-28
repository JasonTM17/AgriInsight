package com.agriinsight.backend.realtime.infrastructure;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** Verifies that database-backed DLT provenance is enforced under the worker role. */
@Testcontainers
class RealtimeOperationalAlertEvidenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2027-09-01T12:00:00Z");
    private static final UUID FORGED_EVENT_ID =
            UUID.fromString("79000000-0000-0000-0000-000000000001");

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

        evaluator.observeDeadLetter(new RealtimeOperationalEvent(
                FORGED_EVENT_ID,
                TENANT_A,
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                NOW.plus(Duration.ofDays(1)),
                "a".repeat(64),
                "agriinsight.operational.v1.dlt",
                0,
                1));

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

    private static RealtimeOperationalAlertEvaluator evaluator(JdbcTemplate alertWorker) {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(
                alertWorker.getDataSource()));
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

    private static JdbcTemplate jdbcTemplate(String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                PostgresIntegrationSupport.jdbcUrl(POSTGRESQL, "agriinsight"), username, password);
        return new JdbcTemplate(dataSource);
    }
}
