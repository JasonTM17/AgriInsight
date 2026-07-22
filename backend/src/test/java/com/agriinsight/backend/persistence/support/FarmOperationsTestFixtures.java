package com.agriinsight.backend.persistence.support;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class FarmOperationsTestFixtures {

    public static final UUID TENANT_A =
            UUID.fromString("10000000-0000-0000-0000-000000000041");

    private FarmOperationsTestFixtures() {
    }

    public static void migrateAndSeed(PostgreSQLContainer container) throws Exception {
        bootstrapRoles(container, "agriinsight");
        migrate(container, "agriinsight");
        try (var operator = operatorConnection(container, "agriinsight")) {
            execute(
                    operator,
                    SqlTestResources.projectFile(
                            "backend/src/test/resources/sql/farm-operations-fixtures.sql"));
        }
    }

    public static Connection tenantRuntimeConnection(PostgreSQLContainer container)
            throws SQLException {
        Connection runtime = runtimeConnection(container, "agriinsight");
        beginTenant(runtime);
        return runtime;
    }

    public static void assertRuntimeStatementRejected(
            PostgreSQLContainer container, String sql) throws Exception {
        try (var runtime = tenantRuntimeConnection(container)) {
            assertThatThrownBy(() -> execute(runtime, sql)).isInstanceOf(SQLException.class);
            runtime.rollback();
        }
    }

    public static String activityLogInsert(String id, String factValues) {
        return """
                INSERT INTO activity_logs (
                    id, tenant_id, activity_id, employee_id, author_profile_id, occurred_at,
                    quantity, unit_code, evidence_uri, corrects_log_id,
                    correction_kind, correction_reason)
                VALUES ('%s', '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000007',
                        '41000000-0000-0000-0000-000000000004',
                        '41000000-0000-0000-0000-000000000005',
                        TIMESTAMPTZ '2027-09-01 02:30:00Z', %s)
                """.formatted(id, factValues);
    }

    public static String harvestInsert(String id, String factValues) {
        return """
                INSERT INTO harvests (
                    id, tenant_id, farm_id, field_id, season_id, crop_id, recorded_by_profile_id,
                    occurred_on, quantity_kg, waste_quantity_kg,
                    corrects_harvest_id, correction_kind, correction_reason)
                VALUES ('%s', '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000003',
                        '41000000-0000-0000-0000-000000000006',
                        '41000000-0000-0000-0000-000000000002',
                        '41000000-0000-0000-0000-000000000005', DATE '2027-09-01', %s)
                """.formatted(id, factValues);
    }

    public static void beginTenant(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true), "
                        + "set_config('app.profile_id', '41000000-0000-0000-0000-000000000005', true)")) {
            statement.setString(1, TENANT_A.toString());
            statement.executeQuery();
        }
    }
}
