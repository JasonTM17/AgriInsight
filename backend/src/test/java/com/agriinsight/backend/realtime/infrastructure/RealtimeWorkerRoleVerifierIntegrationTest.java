package com.agriinsight.backend.realtime.infrastructure;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves the restricted login rejects released-schema, grant, policy, and privilege drift. */
@Testcontainers
class RealtimeWorkerRoleVerifierIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void acceptsTheInstalledReleaseSchemaAndRestrictedWorkerRole() {
        assertThatCode(() -> verifier().verify()).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnsuccessfulReleasedSchemaVersion() throws Exception {
        assertRejectedDuringDrift(
                "UPDATE flyway_schema_history SET success = FALSE WHERE version = '27'",
                "UPDATE flyway_schema_history SET success = TRUE WHERE version = '27'",
                "operational alert worker expected schema version is not installed");
    }

    @Test
    void rejectsAnUnsuccessfulLatestRequiredGrantsMigration() throws Exception {
        String latestRepeatable = """
                installed_rank = (
                    SELECT max(installed_rank)
                      FROM flyway_schema_history
                     WHERE script = 'R__tenant_rls_helpers_and_grants.sql'
                )
                """;
        assertRejectedDuringDrift(
                "UPDATE flyway_schema_history SET success = FALSE WHERE " + latestRepeatable,
                "UPDATE flyway_schema_history SET success = TRUE WHERE " + latestRepeatable,
                "operational alert worker required grants migration is not current");
    }

    @Test
    void rejectsARevokedRequiredReceiptGrant() throws Exception {
        assertRejectedDuringDrift(
                """
                REVOKE SELECT (event_id, tenant_id)
                    ON realtime_event_receipts FROM agriinsight_alert_worker
                """,
                """
                GRANT SELECT (event_id, tenant_id)
                    ON realtime_event_receipts TO agriinsight_alert_worker
                """,
                "operational alert worker database role verification failed");
    }

    @Test
    void rejectsARequiredReceiptPolicyAssignedToTheWrongRole() throws Exception {
        assertRejectedDuringDrift(
                """
                ALTER POLICY alert_worker_realtime_event_receipts_read
                    ON realtime_event_receipts TO PUBLIC
                """,
                """
                ALTER POLICY alert_worker_realtime_event_receipts_read
                    ON realtime_event_receipts TO agriinsight_alert_worker
                """,
                "operational alert worker database role verification failed");
    }

    @Test
    void rejectsAForbiddenBusinessTableGrant() throws Exception {
        assertRejectedDuringDrift(
                "GRANT SELECT ON farms TO agriinsight_alert_worker",
                "REVOKE SELECT ON farms FROM agriinsight_alert_worker",
                "operational alert worker database role verification failed");
    }

    @Test
    void rejectsAColumnLevelWriteGrantOnSchemaHistory() throws Exception {
        assertRejectedDuringDrift(
                """
                GRANT UPDATE (success)
                    ON flyway_schema_history TO agriinsight_alert_worker
                """,
                """
                REVOKE UPDATE (success)
                    ON flyway_schema_history FROM agriinsight_alert_worker
                """,
                "operational alert worker database role verification failed");
    }

    private static void assertRejectedDuringDrift(
            String introduceDrift,
            String repairDrift,
            String expectedMessage) throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, introduceDrift);
            try {
                assertThatThrownBy(() -> verifier().verify())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(expectedMessage);
            } finally {
                execute(operator, repairDrift);
            }
        }
    }

    private static RealtimeWorkerRoleVerifier verifier() {
        return new RealtimeWorkerRoleVerifier(
                alertWorkerJdbcTemplate(),
                workerProperties(),
                alertProperties(),
                kafkaProperties());
    }

    private static JdbcTemplate alertWorkerJdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
                PostgresIntegrationSupport.jdbcUrl(POSTGRESQL, "agriinsight"),
                PostgresIntegrationSupport.ALERT_WORKER,
                PostgresIntegrationSupport.ALERT_WORKER_PASSWORD));
    }

    private static RealtimeWorkerProperties workerProperties() {
        return new RealtimeWorkerProperties(
                false,
                false,
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                "agriinsight.operational.v1",
                "agriinsight.operational.v1.dlt",
                3,
                (short) 1,
                262_144);
    }

    private static RealtimeAlertWorkerProperties alertProperties() {
        return new RealtimeAlertWorkerProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                2,
                100,
                Duration.ofSeconds(20),
                "agriinsight-alert-observer-v1",
                "agriinsight.operational.v1.alert-observer-failure",
                2,
                Duration.ofMillis(500));
    }

    private static KafkaProperties kafkaProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.getConsumer().setGroupId("agriinsight-realtime-v1");
        return properties;
    }
}
