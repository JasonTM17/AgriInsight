package com.agriinsight.backend.realtime.infrastructure;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
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

/** Proves the restricted login can verify only the installed release schema and its own grants. */
@Testcontainers
class RealtimeWorkerRoleVerifierIntegrationTest {

    private static final String EXPECTED_SCHEMA_VERSION = "27";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void acceptsTheInstalledSchemaAndRestrictedWorkerRole() {
        assertThatCode(() -> verifier(EXPECTED_SCHEMA_VERSION).verify()).doesNotThrowAnyException();
    }

    @Test
    void failsClosedBeforeStartingWhenTheExpectedVersionIsNotInstalled() {
        assertThatThrownBy(() -> verifier("28").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker expected schema version is not installed");
    }

    private static RealtimeWorkerRoleVerifier verifier(String expectedSchemaVersion) {
        return new RealtimeWorkerRoleVerifier(
                alertWorkerJdbcTemplate(),
                workerProperties(),
                alertProperties(),
                kafkaProperties(),
                expectedSchemaVersion);
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
