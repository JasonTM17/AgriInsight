package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeAlertAcknowledgement;
import com.agriinsight.backend.realtime.application.RealtimeAlertRecoveryTransition;
import com.agriinsight.backend.realtime.application.RealtimeOpenOperationalAlert;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertAcknowledgementStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCondition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertAcknowledgementStore;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertStore;
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
class RealtimeOperationalAlertStoreIntegrationTest {

    private static final UUID PROFILE_A =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final Instant OBSERVED_AT = Instant.parse("2027-09-01T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void workerStoreDeduplicatesResolvesAndReopensOneAlert() throws Exception {
        JdbcTemplate realtime = jdbcTemplate(
                PostgresIntegrationSupport.REALTIME, PostgresIntegrationSupport.REALTIME_PASSWORD);
        TransactionTemplate transaction = transaction(realtime);
        PostgresRealtimeOperationalAlertStore store = new PostgresRealtimeOperationalAlertStore(realtime);
        RealtimeOperationalAlertCondition condition = backlogCondition();

        transaction.executeWithoutResult(status -> {
            store.upsert(condition, OBSERVED_AT);
            store.upsert(condition, OBSERVED_AT.plusSeconds(30));
            List<RealtimeOpenOperationalAlert> open = store.findOpenAlerts(
                    RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, 10);
            assertThat(open).singleElement();
            store.recordClean(
                    open.getFirst(),
                    new RealtimeAlertRecoveryTransition(OBSERVED_AT.plusSeconds(60), 1, false),
                    OBSERVED_AT.plusSeconds(60));
        });
        transaction.executeWithoutResult(status -> {
            RealtimeOpenOperationalAlert open = store.findOpenAlerts(
                    RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, 10).getFirst();
            store.recordClean(
                    open,
                    new RealtimeAlertRecoveryTransition(OBSERVED_AT.plusSeconds(60), 2, true),
                    OBSERVED_AT.plusSeconds(120));
        });

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                    """)).isEqualTo(1);
            assertThat(scalar(operator, """
                    SELECT state
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                    """)).isEqualTo("RESOLVED");
        }

        transaction.executeWithoutResult(status -> store.upsert(condition, OBSERVED_AT.plusSeconds(180)));

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(scalar(operator, """
                    SELECT state
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                    """)).isEqualTo("OPEN");
        }
    }

    @Test
    void acknowledgementCopiesTheCurrentObservationAndAllowsALaterRecurrence() throws Exception {
        JdbcTemplate realtime = jdbcTemplate(
                PostgresIntegrationSupport.REALTIME, PostgresIntegrationSupport.REALTIME_PASSWORD);
        TransactionTemplate realtimeTransaction = transaction(realtime);
        PostgresRealtimeOperationalAlertStore workerStore = new PostgresRealtimeOperationalAlertStore(realtime);
        RealtimeOperationalAlertCondition condition = new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                TENANT_A,
                UUID.fromString("77000000-0000-0000-0000-000000000020"),
                OBSERVED_AT);
        realtimeTransaction.executeWithoutResult(status -> workerStore.upsert(condition, OBSERVED_AT));
        realtimeTransaction.executeWithoutResult(status -> workerStore.upsert(
                condition, OBSERVED_AT.plusSeconds(30)));
        List<RealtimeOperationalAlertCondition> observedConditions = realtimeTransaction.execute(
                status -> workerStore.findConditions(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, OBSERVED_AT, 10));
        assertThat(observedConditions)
                .singleElement()
                .extracting(RealtimeOperationalAlertCondition::sourceEventId)
                .isEqualTo(condition.sourceEventId());
        realtimeTransaction.executeWithoutResult(status -> realtime.update("""
                INSERT INTO realtime_event_receipts (
                    event_id, tenant_id, checksum, topic, partition_id, broker_offset)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                condition.sourceEventId(),
                TENANT_A,
                "a".repeat(64),
                "agriinsight.operational.v1",
                0,
                909));
        List<RealtimeOperationalAlertCondition> clearedConditions = realtimeTransaction.execute(
                status -> workerStore.findConditions(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, OBSERVED_AT, 10));
        assertThat(clearedConditions).isEmpty();

        UUID alertId;
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            alertId = UUID.fromString(scalar(operator, """
                    SELECT id
                      FROM realtime_operational_alerts
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND policy_code = 'REALTIME_DLT_RECORD'
                       AND source_event_id = '77000000-0000-0000-0000-000000000020'
                    """));
            assertThat(scalar(operator, """
                    SELECT to_char(last_observed_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
                      FROM realtime_operational_alerts
                     WHERE id = '%s'
                    """.formatted(alertId))).isEqualTo("2027-09-01T00:00:00Z");
        }

        JdbcTemplate runtime = jdbcTemplate(
                PostgresIntegrationSupport.RUNTIME, PostgresIntegrationSupport.RUNTIME_PASSWORD);
        TransactionTemplate runtimeTransaction = transaction(runtime);
        RealtimeOperationalAlertAcknowledgementStore acknowledgements =
                new PostgresRealtimeOperationalAlertAcknowledgementStore(runtime);
        RealtimeAlertAcknowledgement first = runtimeTransaction.execute(status -> {
            bindRuntimeScope(runtime);
            return acknowledgements.acknowledge(TENANT_A, PROFILE_A, alertId, OBSERVED_AT.plusSeconds(5));
        });
        RealtimeAlertAcknowledgement duplicate = runtimeTransaction.execute(status -> {
            bindRuntimeScope(runtime);
            return acknowledgements.acknowledge(TENANT_A, PROFILE_A, alertId, OBSERVED_AT.plusSeconds(6));
        });

        assertThat(first.acknowledgedObservationAt()).isEqualTo(OBSERVED_AT);
        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();

        Instant laterObservation = OBSERVED_AT.plusSeconds(300);
        realtimeTransaction.executeWithoutResult(status -> workerStore.upsert(condition, laterObservation));
        RealtimeAlertAcknowledgement recurrence = runtimeTransaction.execute(status -> {
            bindRuntimeScope(runtime);
            return acknowledgements.acknowledge(TENANT_A, PROFILE_A, alertId, laterObservation.plusSeconds(5));
        });

        assertThat(recurrence.acknowledgedObservationAt()).isEqualTo(laterObservation);
        assertThat(recurrence.created()).isTrue();
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM realtime_alert_acknowledgement_revisions
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND alert_id = '%s'
                       AND profile_id = '41000000-0000-0000-0000-000000000005'
                    """.formatted(alertId))).isEqualTo(2);
        }
    }

    private static RealtimeOperationalAlertCondition backlogCondition() {
        return new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                TENANT_A,
                null,
                OBSERVED_AT.minusSeconds(600));
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

    private static void bindRuntimeScope(JdbcTemplate runtime) {
        runtime.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, TENANT_A.toString());
        runtime.queryForObject("SELECT set_config('app.profile_id', ?, true)", String.class, PROFILE_A.toString());
    }
}
