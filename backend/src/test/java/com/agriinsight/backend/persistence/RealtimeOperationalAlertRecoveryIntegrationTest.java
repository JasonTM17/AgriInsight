package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeAlertRecoveryTransition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCondition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertRecoveryCandidate;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertScanStore;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class RealtimeOperationalAlertRecoveryIntegrationTest {

    private static final Instant NOW = Instant.parse("2027-09-01T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void recoveryReaderRechecksCurrentConditionsAndRotatesRowsAfterEachBoundedMutation() {
        JdbcTemplate alertWorker = jdbcTemplate(
                PostgresIntegrationSupport.ALERT_WORKER,
                PostgresIntegrationSupport.ALERT_WORKER_PASSWORD);
        TransactionTemplate alertWorkerTransaction = transaction(alertWorker);
        PostgresRealtimeOperationalAlertStore alertStore =
                new PostgresRealtimeOperationalAlertStore(alertWorker);
        PostgresRealtimeOperationalAlertScanStore scanStore =
                new PostgresRealtimeOperationalAlertScanStore(alertWorker);
        RealtimeOperationalAlertCondition first = dltCondition(1, NOW.minusSeconds(300));
        RealtimeOperationalAlertCondition second = dltCondition(2, NOW.minusSeconds(240));
        RealtimeOperationalAlertCondition third = dltCondition(3, NOW.minusSeconds(180));

        alertWorkerTransaction.executeWithoutResult(status -> {
            alertStore.upsert(first, NOW.minusSeconds(300));
            alertStore.upsert(second, NOW.minusSeconds(240));
            alertStore.upsert(third, NOW.minusSeconds(180));
        });
        insertReceipt(second.sourceEventId());

        List<RealtimeOperationalAlertRecoveryCandidate> firstPage = alertWorkerTransaction.execute(
                status -> scanStore.findRecoveryCandidates(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                        NOW,
                        NOW,
                        3));

        assertThat(firstPage).hasSize(3);
        assertThat(firstPage.getFirst().currentCondition()).contains(first);
        assertThat(firstPage.get(1).currentCondition()).isEmpty();
        assertThat(firstPage.get(2).currentCondition()).contains(third);

        alertWorkerTransaction.executeWithoutResult(status -> {
            alertStore.upsert(firstPage.getFirst().currentCondition().orElseThrow(), NOW);
            alertStore.recordClean(
                    firstPage.get(1).alert(),
                    new RealtimeAlertRecoveryTransition(NOW, 1, false),
                    NOW,
                    NOW);
        });
        assertThat(alertWorker.queryForObject(
                        """
                        SELECT count(*)
                          FROM realtime_operational_alerts
                         WHERE id IN (?, ?)
                           AND last_evaluated_at = ?
                        """,
                        Integer.class,
                        firstPage.getFirst().alert().id(),
                        firstPage.get(1).alert().id(),
                        Timestamp.from(NOW)))
                .isEqualTo(2);

        List<RealtimeOperationalAlertRecoveryCandidate> resumedPage = alertWorkerTransaction.execute(
                status -> scanStore.findRecoveryCandidates(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                        NOW,
                        NOW,
                        3));

        assertThat(resumedPage).singleElement().satisfies(candidate -> {
            assertThat(candidate.alert()).isEqualTo(firstPage.get(2).alert());
            assertThat(candidate.currentCondition()).contains(third);
        });
    }

    private static RealtimeOperationalAlertCondition dltCondition(int sequence, Instant sourceOccurredAt) {
        return new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                TENANT_A,
                UUID.fromString("73000000-0000-0000-0000-00000000000" + sequence),
                sourceOccurredAt);
    }

    private static void insertReceipt(UUID eventId) {
        JdbcTemplate realtime = jdbcTemplate(
                PostgresIntegrationSupport.REALTIME, PostgresIntegrationSupport.REALTIME_PASSWORD);
        transaction(realtime).executeWithoutResult(status -> realtime.update("""
                INSERT INTO realtime_event_receipts (
                    event_id, tenant_id, checksum, topic, partition_id, broker_offset)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                eventId,
                TENANT_A,
                "a".repeat(64),
                "agriinsight.operational.v1",
                0,
                1));
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
