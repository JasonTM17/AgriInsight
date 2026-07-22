package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.assertRuntimeStatementRejected;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class InventorySchemaIntegrationTest {

    private static final List<String> INVENTORY_TABLES = List.of(
            "warehouses",
            "materials",
            "suppliers",
            "user_warehouse_assignments",
            "inventory_transactions",
            "inventory_transaction_lot_allocations",
            "stock_lots",
            "stock_balances");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void inventoryTablesForceTenantRlsAndHaveTenantLeadingIndexes() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_class relation
                      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relname IN (
                            'warehouses', 'materials', 'suppliers',
                            'user_warehouse_assignments', 'inventory_transactions',
                            'inventory_transaction_lot_allocations', 'stock_lots', 'stock_balances')
                       AND relation.relrowsecurity
                       AND relation.relforcerowsecurity
                    """)).isEqualTo(INVENTORY_TABLES.size());
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_policies
                     WHERE schemaname = 'public'
                       AND tablename IN (
                            'warehouses', 'materials', 'suppliers',
                            'user_warehouse_assignments', 'inventory_transactions',
                            'inventory_transaction_lot_allocations', 'stock_lots', 'stock_balances')
                       AND policyname IN ('runtime_tenant_isolation', 'migration_tenant_isolation')
                    """)).isEqualTo(INVENTORY_TABLES.size() * 2L);
            assertThat(count(operator, """
                    SELECT count(DISTINCT tablename)
                      FROM pg_indexes
                     WHERE schemaname = 'public'
                       AND tablename IN (
                            'warehouses', 'materials', 'suppliers',
                            'user_warehouse_assignments', 'inventory_transactions',
                            'inventory_transaction_lot_allocations', 'stock_lots', 'stock_balances')
                       AND indexdef ~ '\\(tenant_id(,|\\))'
                    """)).isEqualTo(INVENTORY_TABLES.size());
        }
    }

    @Test
    void runtimePrivilegesPreserveImmutableLedgerAndConstrainedProjections() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('inventory_transactions'),
                            ('inventory_transaction_lot_allocations')) immutable(table_name)
                     WHERE has_table_privilege(
                                'agriinsight_runtime', 'public.' || table_name, 'SELECT')
                       AND has_table_privilege(
                                'agriinsight_runtime', 'public.' || table_name, 'INSERT')
                       AND NOT has_table_privilege(
                                'agriinsight_runtime', 'public.' || table_name, 'UPDATE')
                       AND NOT has_table_privilege(
                                'agriinsight_runtime', 'public.' || table_name, 'DELETE')
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('stock_lots', 'available_quantity'),
                            ('stock_balances', 'quantity_on_hand'),
                            ('user_warehouse_assignments', 'revoked_at')) expected(table_name, column_name)
                     WHERE has_column_privilege(
                            'agriinsight_runtime', 'public.' || table_name, column_name, 'UPDATE')
                    """)).isEqualTo(3);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM unnest(ARRAY[
                            'warehouses', 'materials', 'suppliers',
                            'user_warehouse_assignments', 'inventory_transactions',
                            'inventory_transaction_lot_allocations', 'stock_lots', 'stock_balances'])
                           AS scoped(table_name)
                     WHERE NOT has_table_privilege(
                            'agriinsight_runtime', 'public.' || table_name, 'DELETE')
                    """)).isEqualTo(INVENTORY_TABLES.size());
        }
    }

    @Test
    void runtimeRowsFailClosedWithoutTenantAndRejectNoncanonicalCodes() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            for (String table : INVENTORY_TABLES) {
                assertThat(count(runtime, "SELECT count(*) FROM " + table))
                        .as("fail-closed row count for %s", table)
                        .isZero();
            }
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, """
                    INSERT INTO warehouses (id, tenant_id, code, display_name)
                    VALUES ('51000000-0000-0000-0000-000000000001',
                            '10000000-0000-0000-0000-000000000041',
                            'WH-TEST', 'Kho kiểm thử')
                    """);
            assertThat(count(runtime, "SELECT count(*) FROM warehouses")).isEqualTo(1);
            runtime.rollback();
        }

        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO warehouses (id, tenant_id, code, display_name)
                VALUES ('51000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000041',
                        'not-canonical', 'Kho sai mã')
                """);
    }

    @Test
    void representativeInventoryQueriesUseTenantLeadingIndexes() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "SET enable_seqscan = off");
            assertThat(explain(operator, """
                    SELECT id FROM stock_balances
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND warehouse_id = '51000000-0000-0000-0000-000000000001'
                       AND material_id = '52000000-0000-0000-0000-000000000001'
                    """)).anyMatch(line -> line.contains(
                            "Index Scan using ix_stock_balances_tenant_"));
            assertThat(explain(operator, """
                    SELECT id FROM inventory_transactions
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND warehouse_id = '51000000-0000-0000-0000-000000000001'
                       AND occurred_at >= TIMESTAMPTZ '2027-01-01T00:00:00Z'
                     ORDER BY occurred_at DESC, id
                     LIMIT 51
                    """)).anyMatch(line -> line.contains(
                            "ix_inventory_transactions_tenant_warehouse_occurred"));
            assertThat(explain(operator, """
                    SELECT id FROM inventory_transactions
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                     ORDER BY occurred_at DESC, id DESC
                     LIMIT 51
                    """)).anyMatch(line -> line.contains(
                            "ix_inventory_transactions_tenant_occurred"));
            assertThat(explain(operator, """
                    SELECT id FROM stock_lots
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND warehouse_id = '51000000-0000-0000-0000-000000000001'
                       AND material_id = '52000000-0000-0000-0000-000000000001'
                       AND available_quantity > 0
                     ORDER BY expiry_date, received_at, id
                    """)).anyMatch(line -> line.contains(
                            "ix_stock_lots_tenant_warehouse_material_fefo"));
            assertThat(explain(operator, """
                    SELECT id FROM stock_lots
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                     ORDER BY expiry_date, received_at, id
                    """)).anyMatch(line -> line.contains("ix_stock_lots_tenant_fefo_all"));
        }
    }

    private List<String> explain(java.sql.Connection connection, String query) throws Exception {
        List<String> plan = new ArrayList<>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("EXPLAIN (COSTS OFF) " + query)) {
            while (rows.next()) {
                plan.add(rows.getString(1));
            }
        }
        return plan;
    }
}
