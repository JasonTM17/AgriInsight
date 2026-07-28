package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeAlertAcknowledgement;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertAcknowledgementStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertCondition;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertAcknowledgementStore;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertStore;
import java.time.Instant;
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
class RealtimeOperationalAlertAcknowledgementIntegrationTest {

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
    void staleWorkerObservationAfterAcknowledgementCannotRegressLastObservedAt() throws Exception {
        JdbcTemplate alertWorker = jdbcTemplate(
                PostgresIntegrationSupport.ALERT_WORKER,
                PostgresIntegrationSupport.ALERT_WORKER_PASSWORD);
        TransactionTemplate alertWorkerTransaction = transaction(alertWorker);
        PostgresRealtimeOperationalAlertStore alertStore = new PostgresRealtimeOperationalAlertStore(alertWorker);
        Instant currentObservation = OBSERVED_AT.plusSeconds(300);
        alertWorkerTransaction.executeWithoutResult(status -> alertStore.upsert(
                backlogCondition(OBSERVED_AT.minusSeconds(300)), currentObservation));
        UUID alertId = alertWorkerTransaction.execute(status -> alertStore.findOpenAlerts(
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, 10).getFirst().id());

        JdbcTemplate runtime = jdbcTemplate(
                PostgresIntegrationSupport.RUNTIME, PostgresIntegrationSupport.RUNTIME_PASSWORD);
        RealtimeOperationalAlertAcknowledgementStore acknowledgements =
                new PostgresRealtimeOperationalAlertAcknowledgementStore(runtime);
        RealtimeAlertAcknowledgement acknowledgement = acknowledge(
                transaction(runtime), runtime, acknowledgements, alertId, currentObservation.plusSeconds(5));
        assertThat(acknowledgement.acknowledgedObservationAt()).isEqualTo(currentObservation);

        alertWorkerTransaction.executeWithoutResult(status -> alertStore.upsert(
                backlogCondition(OBSERVED_AT.minusSeconds(450)), OBSERVED_AT.plusSeconds(120)));

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(scalar(operator, """
                    SELECT to_char(last_observed_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
                      FROM realtime_operational_alerts
                     WHERE id = '%s'
                    """.formatted(alertId))).isEqualTo("2027-09-01T00:05:00Z");
        }
    }

    private static RealtimeAlertAcknowledgement acknowledge(
            TransactionTemplate transaction,
            JdbcTemplate runtime,
            RealtimeOperationalAlertAcknowledgementStore acknowledgements,
            UUID alertId,
            Instant acknowledgedAt) {
        return transaction.execute(status -> {
            runtime.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, TENANT_A.toString());
            runtime.queryForObject("SELECT set_config('app.profile_id', ?, true)", String.class, PROFILE_A.toString());
            return acknowledgements.acknowledge(TENANT_A, PROFILE_A, alertId, acknowledgedAt);
        });
    }

    private static RealtimeOperationalAlertCondition backlogCondition(Instant sourceOccurredAt) {
        return new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                TENANT_A,
                null,
                sourceOccurredAt);
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
