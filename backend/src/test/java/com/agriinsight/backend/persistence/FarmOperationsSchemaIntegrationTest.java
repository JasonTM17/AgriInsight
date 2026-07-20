package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.assertRuntimeStatementRejected;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmOperationsSchemaIntegrationTest {

    private static final List<String> PHASE_FOUR_TABLES = List.of(
            "farms",
            "crops",
            "fields",
            "seasons",
            "employees",
            "user_farm_assignments",
            "activity_types",
            "activities",
            "activity_assignees",
            "activity_logs",
            "harvests");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void everyPhaseFourTableHasForceRlsAndReviewedRuntimePrivileges() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_class relation
                      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relname IN (
                            'farms', 'crops', 'fields', 'seasons', 'employees',
                            'user_farm_assignments', 'activity_types', 'activities',
                            'activity_assignees', 'activity_logs', 'harvests')
                       AND relation.relrowsecurity
                       AND relation.relforcerowsecurity
                    """)).isEqualTo(11);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_policies
                     WHERE schemaname = 'public'
                       AND tablename IN (
                            'farms', 'crops', 'fields', 'seasons', 'employees',
                            'user_farm_assignments', 'activity_types', 'activities',
                            'activity_assignees', 'activity_logs', 'harvests')
                       AND policyname IN ('runtime_tenant_isolation', 'migration_tenant_isolation')
                       AND permissive = 'PERMISSIVE'
                    """)).isEqualTo(22);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES ('activity_logs'), ('harvests')) AS immutable(table_name)
                     WHERE has_table_privilege('agriinsight_runtime', 'public.' || table_name, 'SELECT')
                       AND has_table_privilege('agriinsight_runtime', 'public.' || table_name, 'INSERT')
                       AND NOT has_table_privilege('agriinsight_runtime', 'public.' || table_name, 'UPDATE')
                       AND NOT has_table_privilege('agriinsight_runtime', 'public.' || table_name, 'DELETE')
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(DISTINCT tablename)
                      FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND tablename IN (
                            'farms', 'crops', 'fields', 'seasons', 'employees',
                            'user_farm_assignments', 'activity_types', 'activities',
                            'activity_assignees', 'activity_logs', 'harvests')
                       AND indexdef ~ '\\(tenant_id(,|\\))'
                    """)).isEqualTo(11);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('farms'), ('crops'), ('fields'), ('seasons'), ('employees'),
                            ('user_farm_assignments'), ('activity_types'), ('activities'),
                            ('activity_assignees'), ('activity_logs'), ('harvests')) AS scoped(table_name)
                     WHERE NOT has_table_privilege(
                            'agriinsight_runtime', 'public.' || table_name, 'DELETE')
                    """)).isEqualTo(11);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('activities', 'title', TRUE),
                            ('activities', 'season_id', FALSE),
                            ('seasons', 'crop_id', FALSE),
                            ('user_farm_assignments', 'revoked_at', TRUE),
                            ('user_farm_assignments', 'farm_id', FALSE),
                            ('activity_assignees', 'revoked_at', TRUE),
                            ('activity_assignees', 'employee_id', FALSE))
                           AS expected(table_name, column_name, allowed)
                     WHERE has_column_privilege(
                            'agriinsight_runtime',
                            'public.' || table_name,
                            column_name,
                            'UPDATE') = allowed
                    """)).isEqualTo(7);
        }
    }

    @Test
    void everyPhaseFourTableIsVisibleOnlyInsideTheMatchingTenantContext() throws Exception {
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            for (String table : PHASE_FOUR_TABLES) {
                assertThat(count(runtime, "SELECT count(*) FROM " + table))
                        .as("tenant-visible row count for %s", table)
                        .isEqualTo(1);
                assertThat(count(runtime, """
                        SELECT count(*) FROM %s
                        WHERE tenant_id <> '10000000-0000-0000-0000-000000000041'
                        """.formatted(table)))
                        .as("cross-tenant row count for %s", table)
                        .isZero();
            }
            runtime.rollback();
        }

        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            for (String table : PHASE_FOUR_TABLES) {
                assertThat(count(runtime, "SELECT count(*) FROM " + table))
                        .as("fail-closed row count for %s", table)
                        .isZero();
            }
        }
    }

    @Test
    void tenantIsolationAndCompositeParentKeysFailClosed() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(runtime, "SELECT count(*) FROM farms")).isZero();
        }
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO farms (id, tenant_id, code, display_name)
                VALUES ('42000000-0000-0000-0000-000000000099',
                        '10000000-0000-0000-0000-000000000042', 'CROSS', 'Cross tenant')
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES ('41000000-0000-0000-0000-000000000099',
                        '10000000-0000-0000-0000-000000000041',
                        '42000000-0000-0000-0000-000000000001', 'BAD-PARENT', 'Bad parent', 1)
                """);
    }

    @Test
    void areaSeasonDateAndBudgetConstraintsRejectInvalidFactsIndependently() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES ('41000000-0000-0000-0000-000000000098',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001', 'BAD-AREA', 'Bad area', 0)
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares, status, budget_vnd)
                VALUES (
                    '41000000-0000-0000-0000-000000000097',
                    '10000000-0000-0000-0000-000000000041',
                    '41000000-0000-0000-0000-000000000001',
                    '41000000-0000-0000-0000-000000000003',
                    '41000000-0000-0000-0000-000000000002',
                    'BAD-DATES', 'Bad dates', DATE '2027-12-31', DATE '2027-01-01', 1, 'PLANNED', 0)
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares, status, budget_vnd)
                VALUES (
                    '41000000-0000-0000-0000-000000000096',
                    '10000000-0000-0000-0000-000000000041',
                    '41000000-0000-0000-0000-000000000001',
                    '41000000-0000-0000-0000-000000000003',
                    '41000000-0000-0000-0000-000000000002',
                    'BAD-BUDGET', 'Bad budget', DATE '2027-01-01', DATE '2027-12-31', 1, 'PLANNED', -1)
                """);
    }

}
