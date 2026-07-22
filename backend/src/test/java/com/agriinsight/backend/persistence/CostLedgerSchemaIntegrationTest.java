package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CostLedgerSchemaIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, seedEntries());
        }
    }

    @Test
    void schemaProvidesFixedCategoriesImmutablePrivilegesAndForcedRls() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, "SELECT count(*) FROM cost_categories")).isEqualTo(6);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_class relation
                      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relname = 'operating_cost_entries'
                       AND relation.relrowsecurity
                       AND relation.relforcerowsecurity
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*)
                      WHERE has_table_privilege(
                                'agriinsight_runtime',
                                'public.operating_cost_entries', 'SELECT')
                        AND has_table_privilege(
                                'agriinsight_runtime',
                                'public.operating_cost_entries', 'INSERT')
                        AND NOT has_table_privilege(
                                'agriinsight_runtime',
                                'public.operating_cost_entries', 'UPDATE')
                        AND NOT has_table_privilege(
                                'agriinsight_runtime',
                                'public.operating_cost_entries', 'DELETE')
                    """)).isEqualTo(1);
        }
    }

    @Test
    void databaseRejectsInvalidTargetAmountAndReversalShapes() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertRejected(operator, entry(
                    "61000000-0000-0000-0000-000000000010",
                    "FIELD", "'41000000-0000-0000-0000-000000000001'",
                    "'41000000-0000-0000-0000-000000000003'", "NULL", "NULL",
                    "LABOR", "1.00", "POSTING", "NULL", "'1'"));
            assertRejected(operator, entry(
                    "61000000-0000-0000-0000-000000000011",
                    "TENANT", "NULL", "NULL", "NULL", "NULL",
                    "LABOR", "0.00", "POSTING", "NULL", "'2'"));
            assertRejected(operator, entry(
                    "61000000-0000-0000-0000-000000000012",
                    "FARM", "'41000000-0000-0000-0000-000000000001'",
                    "NULL", "NULL", "NULL", "MATERIAL", "100.00", "REVERSAL",
                    "'61000000-0000-0000-0000-000000000001'", "'3'"));
        }
    }

    @Test
    void reversalCopiesFinancialDimensionsAndCannotChainOrRepeat() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, entry(
                    "61000000-0000-0000-0000-000000000020",
                    "FARM", "'41000000-0000-0000-0000-000000000001'",
                    "NULL", "NULL", "NULL", "LABOR", "100.00", "REVERSAL",
                    "'61000000-0000-0000-0000-000000000001'", "'4'"));
            assertRejected(operator, entry(
                    "61000000-0000-0000-0000-000000000021",
                    "FARM", "'41000000-0000-0000-0000-000000000001'",
                    "NULL", "NULL", "NULL", "LABOR", "100.00", "REVERSAL",
                    "'61000000-0000-0000-0000-000000000001'", "'5'"));
            assertRejected(operator, entry(
                    "61000000-0000-0000-0000-000000000022",
                    "FARM", "'41000000-0000-0000-0000-000000000001'",
                    "NULL", "NULL", "NULL", "LABOR", "100.00", "REVERSAL",
                    "'61000000-0000-0000-0000-000000000020'", "'6'"));
        }
    }

    @Test
    void rlsAllowsAssignedFarmReadsButDeniesFinanceToInventoryManager() throws Exception {
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM operating_cost_entries")).isZero();
            runtime.rollback();
        }

        replaceRole("INVENTORY_MANAGER", "FARM_MANAGER", "61000000-0000-0000-0000-000000000030");
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, """
                    SELECT count(*) FROM operating_cost_entries
                    WHERE entry_kind = 'POSTING'
                    """))
                    .isEqualTo(4);
            assertThatThrownBy(() -> execute(runtime, entry(
                    "61000000-0000-0000-0000-000000000031",
                    "FARM", "'41000000-0000-0000-0000-000000000001'",
                    "NULL", "NULL", "NULL", "LABOR", "1.00", "POSTING", "NULL", "'7'")))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        replaceRole("FARM_MANAGER", "EXECUTIVE", "61000000-0000-0000-0000-000000000032");
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, """
                    SELECT count(*) FROM operating_cost_entries
                    WHERE entry_kind = 'POSTING'
                    """))
                    .isEqualTo(5);
            runtime.rollback();
        }
    }

    private static void replaceRole(String oldRole, String newRole, String assignmentId)
            throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    UPDATE user_roles
                       SET revoked_at = clock_timestamp(), version = version + 1,
                           updated_at = clock_timestamp()
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND user_profile_id = '41000000-0000-0000-0000-000000000005'
                       AND role_code = '%s'
                       AND revoked_at IS NULL;
                    INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                    VALUES ('%s', '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000005', '%s')
                    """.formatted(oldRole, assignmentId, newRole));
        }
    }

    private static String seedEntries() {
        return entry("61000000-0000-0000-0000-000000000001", "FARM",
                "'41000000-0000-0000-0000-000000000001'", "NULL", "NULL", "NULL",
                "LABOR", "100.00", "POSTING", "NULL", "'a'")
                + entry("61000000-0000-0000-0000-000000000002", "FIELD", "NULL",
                "'41000000-0000-0000-0000-000000000003'", "NULL", "NULL",
                "MATERIAL", "200.00", "POSTING", "NULL", "'b'")
                + entry("61000000-0000-0000-0000-000000000003", "SEASON", "NULL", "NULL",
                "'41000000-0000-0000-0000-000000000006'", "NULL",
                "UTILITY", "300.00", "POSTING", "NULL", "'c'")
                + entry("61000000-0000-0000-0000-000000000004", "ACTIVITY", "NULL", "NULL",
                "NULL", "'41000000-0000-0000-0000-000000000007'",
                "TRANSPORT", "400.00", "POSTING", "NULL", "'d'")
                + entry("61000000-0000-0000-0000-000000000005", "TENANT", "NULL", "NULL",
                "NULL", "NULL", "OTHER", "500.00", "POSTING", "NULL", "'e'");
    }

    private static String entry(
            String id, String targetType, String farmId, String fieldId,
            String seasonId, String activityId, String category, String amount,
            String kind, String reversalOf, String commandSeed) {
        return """
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                VALUES ('%s', '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000005', 'POST',
                        '/api/v1/cost-entries', repeat(%s, 64), 1, repeat(%s, 64),
                        'IN_PROGRESS');
                INSERT INTO operating_cost_entries (
                    id, tenant_id, target_type, farm_id, field_id, season_id, activity_id,
                    category_code, amount_vnd, entry_kind, occurred_at, reversal_of,
                    command_reference, recorded_by_profile_id)
                VALUES ('%s', '10000000-0000-0000-0000-000000000041', '%s',
                        %s, %s, %s, %s, '%s', %s, '%s',
                        TIMESTAMPTZ '2027-09-01T02:00:00Z', %s,
                        '%s', '41000000-0000-0000-0000-000000000005');
                """.formatted(
                id, commandSeed, commandSeed,
                id, targetType, farmId, fieldId, seasonId, activityId,
                category, amount, kind, reversalOf, id);
    }

    private static void assertRejected(java.sql.Connection connection, String sql) {
        assertThatThrownBy(() -> execute(connection, sql)).isInstanceOf(SQLException.class);
    }
}
