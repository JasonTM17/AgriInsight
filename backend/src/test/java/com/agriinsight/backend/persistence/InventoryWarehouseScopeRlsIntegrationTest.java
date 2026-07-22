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
class InventoryWarehouseScopeRlsIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO warehouses (id, tenant_id, code, display_name) VALUES
                        ('5a000000-0000-0000-0000-000000000001',
                         '10000000-0000-0000-0000-000000000041', 'WH-RLS-A', 'Assigned'),
                        ('5a000000-0000-0000-0000-000000000002',
                         '10000000-0000-0000-0000-000000000041', 'WH-RLS-B', 'Unassigned');
                    INSERT INTO materials (id, tenant_id, code, display_name, base_unit)
                    VALUES ('5a000000-0000-0000-0000-000000000003',
                            '10000000-0000-0000-0000-000000000041',
                            'MAT-RLS', 'Scoped material', 'KG');
                    INSERT INTO user_warehouse_assignments (
                        id, tenant_id, user_profile_id, warehouse_id)
                    VALUES ('5a000000-0000-0000-0000-000000000004',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000005',
                            '5a000000-0000-0000-0000-000000000001');
                    INSERT INTO stock_balances (
                        id, tenant_id, warehouse_id, material_id, unit_code,
                        quantity_on_hand, inventory_value_vnd) VALUES
                        ('5a000000-0000-0000-0000-000000000005',
                         '10000000-0000-0000-0000-000000000041',
                         '5a000000-0000-0000-0000-000000000001',
                         '5a000000-0000-0000-0000-000000000003', 'KG', 1, 1),
                        ('5a000000-0000-0000-0000-000000000006',
                         '10000000-0000-0000-0000-000000000041',
                         '5a000000-0000-0000-0000-000000000002',
                         '5a000000-0000-0000-0000-000000000003', 'KG', 1, 1);
                    """);
        }
    }

    @Test
    void runtimeInventoryRowsRequireCurrentWarehouseAssignment() throws Exception {
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM stock_balances")).isEqualTo(1);
            assertThatThrownBy(() -> execute(runtime, """
                    INSERT INTO stock_balances (
                        id, tenant_id, warehouse_id, material_id, unit_code,
                        quantity_on_hand, inventory_value_vnd)
                    VALUES ('5a000000-0000-0000-0000-000000000007',
                            '10000000-0000-0000-0000-000000000041',
                            '5a000000-0000-0000-0000-000000000002',
                            '5a000000-0000-0000-0000-000000000003', 'KG', 0, 0)
                    """))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    UPDATE user_roles
                       SET revoked_at = clock_timestamp(), version = version + 1,
                           updated_at = clock_timestamp()
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND user_profile_id = '41000000-0000-0000-0000-000000000005'
                       AND role_code = 'INVENTORY_MANAGER';
                    INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                    VALUES ('5a000000-0000-0000-0000-000000000008',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000005', 'SUPPLIER');
                    """);
        }
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM stock_balances")).isZero();
            runtime.rollback();
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    UPDATE user_roles
                       SET revoked_at = clock_timestamp(), version = version + 1,
                           updated_at = clock_timestamp()
                     WHERE id = '5a000000-0000-0000-0000-000000000008';
                    INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                    VALUES ('5a000000-0000-0000-0000-000000000009',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000005', 'FARM_MANAGER');
                    """);
        }
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM stock_balances")).isEqualTo(1);
            assertThatThrownBy(() -> execute(runtime, """
                    INSERT INTO stock_balances (
                        id, tenant_id, warehouse_id, material_id, unit_code,
                        quantity_on_hand, inventory_value_vnd)
                    VALUES ('5a000000-0000-0000-0000-00000000000a',
                            '10000000-0000-0000-0000-000000000041',
                            '5a000000-0000-0000-0000-000000000001',
                            '5a000000-0000-0000-0000-000000000003', 'KG', 0, 0)
                    """))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    UPDATE user_roles
                       SET revoked_at = NULL, version = version + 1,
                           updated_at = clock_timestamp()
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND user_profile_id = '41000000-0000-0000-0000-000000000005'
                       AND role_code = 'INVENTORY_MANAGER';
                    UPDATE user_warehouse_assignments
                       SET revoked_at = clock_timestamp(), version = version + 1,
                           updated_at = clock_timestamp()
                     WHERE id = '5a000000-0000-0000-0000-000000000004'
                    """);
        }
        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM stock_balances")).isZero();
            runtime.rollback();
        }
    }
}
