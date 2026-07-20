package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.activityLogInsert;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.assertRuntimeStatementRejected;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.harvestInsert;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmFactCorrectionConstraintsIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void activityLogsEnforceEvidenceCorrectionLineageVoidAndImmutability() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, activityLogInsert(
                "41000000-0000-0000-0000-000000000020",
                "100.0000, 'KG', 'file:///etc/passwd', NULL, NULL, NULL"));
        assertRuntimeStatementRejected(POSTGRESQL, activityLogInsert(
                "41000000-0000-0000-0000-000000000021",
                "100.0000, NULL, NULL, NULL, NULL, NULL"));
        assertRuntimeStatementRejected(POSTGRESQL, activityLogInsert(
                "41000000-0000-0000-0000-000000000022",
                "NULL, NULL, NULL, '41000000-0000-0000-0000-000000000022', "
                        + "'REPLACE', 'Self correction'"));

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, activityLogInsert(
                    "41000000-0000-0000-0000-000000000023",
                    "101.0000, 'KG', 'https://evidence.example.test/log-23', "
                            + "'41000000-0000-0000-0000-000000000010', "
                            + "'REPLACE', 'Corrected quantity'"));
            assertThat(count(runtime, """
                    SELECT count(*) FROM activity_logs
                    WHERE id = '41000000-0000-0000-0000-000000000023'
                    """)).isEqualTo(1);
            assertThatThrownBy(() -> execute(runtime, activityLogInsert(
                    "41000000-0000-0000-0000-000000000027",
                    "102.0000, 'KG', NULL, "
                            + "'41000000-0000-0000-0000-000000000010', "
                            + "'REPLACE', 'Competing correction'")))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, activityLogInsert(
                    "41000000-0000-0000-0000-000000000028",
                    "5.0000, 'KG', NULL, NULL, NULL, NULL"));
            execute(runtime, activityLogInsert(
                    "41000000-0000-0000-0000-000000000029",
                    "NULL, NULL, NULL, '41000000-0000-0000-0000-000000000028', "
                            + "'VOID', 'Duplicate log'"));
            assertThat(count(runtime, """
                    SELECT count(*) FROM activity_logs
                    WHERE id = '41000000-0000-0000-0000-000000000029'
                      AND correction_kind = 'VOID'
                    """)).isEqualTo(1);
            runtime.rollback();
        }

        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE activity_logs SET notes = 'Rewritten'
                WHERE id = '41000000-0000-0000-0000-000000000010'
                """);
    }

    @Test
    void harvestsEnforceAmountsCorrectionLineageVoidAndImmutability() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, harvestInsert(
                "41000000-0000-0000-0000-000000000024", "10.000, 11.000, NULL, NULL, NULL"));
        assertRuntimeStatementRejected(POSTGRESQL, harvestInsert(
                "41000000-0000-0000-0000-000000000025",
                "10.000, 1.000, NULL, 'REPLACE', 'Missing target'"));

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, harvestInsert(
                    "41000000-0000-0000-0000-000000000026",
                    "101.000, 2.000, '41000000-0000-0000-0000-000000000011', 'REPLACE', "
                            + "'Corrected quantity'"));
            assertThat(count(runtime, """
                    SELECT count(*) FROM harvests
                    WHERE id = '41000000-0000-0000-0000-000000000026'
                    """)).isEqualTo(1);
            assertThatThrownBy(() -> execute(runtime, harvestInsert(
                    "41000000-0000-0000-0000-000000000030",
                    "102.000, 2.000, '41000000-0000-0000-0000-000000000011', "
                            + "'REPLACE', 'Competing correction'")))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, harvestInsert(
                    "41000000-0000-0000-0000-000000000031",
                    "5.000, 0.000, NULL, NULL, NULL"));
            execute(runtime, harvestInsert(
                    "41000000-0000-0000-0000-000000000032",
                    "0.000, 0.000, '41000000-0000-0000-0000-000000000031', "
                            + "'VOID', 'Duplicate harvest'"));
            assertThat(count(runtime, """
                    SELECT count(*) FROM harvests
                    WHERE id = '41000000-0000-0000-0000-000000000032'
                      AND correction_kind = 'VOID'
                    """)).isEqualTo(1);
            runtime.rollback();
        }

        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE harvests SET quantity_kg = 101
                WHERE id = '41000000-0000-0000-0000-000000000011'
                """);
    }
}
