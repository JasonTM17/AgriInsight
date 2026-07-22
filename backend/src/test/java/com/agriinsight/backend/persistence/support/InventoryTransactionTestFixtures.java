package com.agriinsight.backend.persistence.support;

import java.util.UUID;

public final class InventoryTransactionTestFixtures {

    private static final UUID ASSIGNMENT_ID =
            UUID.fromString("59000000-0000-0000-0000-000000000004");

    private InventoryTransactionTestFixtures() {
    }

    public static void createCatalog(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID profileId,
            UUID warehouseId,
            UUID materialId,
            UUID supplierId) throws Throwable {
        harness.withinTenant(() -> {
            harness.jdbcTemplate().update("""
                    INSERT INTO warehouses (id, tenant_id, code, display_name)
                    VALUES (?, ?, 'WH-LEDGER', 'Ledger Warehouse')
                    """, warehouseId, tenantId);
            harness.jdbcTemplate().update("""
                    INSERT INTO materials (
                        id, tenant_id, code, display_name, base_unit, minimum_stock_quantity)
                    VALUES (?, ?, 'FERT-LEDGER', 'Ledger Fertilizer', 'KG', 10)
                    """, materialId, tenantId);
            harness.jdbcTemplate().update("""
                    INSERT INTO suppliers (id, tenant_id, code, display_name)
                    VALUES (?, ?, 'SUP-LEDGER', 'Ledger Supplier')
                    """, supplierId, tenantId);
            harness.jdbcTemplate().update("""
                    INSERT INTO user_warehouse_assignments (
                        id, tenant_id, user_profile_id, warehouse_id)
                    VALUES (?, ?, ?, ?)
                    """, ASSIGNMENT_ID, tenantId, profileId, warehouseId);
            return null;
        });
    }

    public static void setWarehouseActive(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            boolean active) throws Throwable {
        harness.withinTenant(() -> {
            if (active) {
                updateWarehouse(harness, tenantId, warehouseId, true);
                updateAssignment(harness, tenantId, warehouseId, false);
            } else {
                updateAssignment(harness, tenantId, warehouseId, true);
                updateWarehouse(harness, tenantId, warehouseId, false);
            }
            return null;
        });
    }

    public static UUID lotId(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            UUID materialId,
            String batch) throws Throwable {
        return harness.withinTenant(() -> harness.jdbcTemplate().queryForObject("""
                SELECT id FROM stock_lots
                 WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                   AND batch_code = ?
                """, UUID.class, tenantId, warehouseId, materialId, batch));
    }

    private static void updateWarehouse(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            boolean active) {
        harness.jdbcTemplate().update("""
                UPDATE warehouses SET active = ?, version = version + 1
                 WHERE tenant_id = ? AND id = ?
                """, active, tenantId, warehouseId);
    }

    private static void updateAssignment(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            boolean revoked) {
        harness.jdbcTemplate().update("""
                UPDATE user_warehouse_assignments
                   SET revoked_at = CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                       version = version + 1, updated_at = clock_timestamp()
                 WHERE tenant_id = ? AND warehouse_id = ?
                """, revoked, tenantId, warehouseId);
    }
}
