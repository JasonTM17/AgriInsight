package com.agriinsight.backend.persistence.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class InventoryReconciliationDriftFixtures {

    private InventoryReconciliationDriftFixtures() {
    }

    static void insertMisallocatedIssueReversal(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            UUID materialId,
            UUID profileId) {
        UUID sourceLotId = lotId(harness, tenantId, warehouseId, "LATE");
        UUID wrongLotId = lotId(harness, tenantId, warehouseId, "EARLY");
        UUID issueId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();

        harness.jdbcTemplate().update("""
                INSERT INTO inventory_transactions (
                    id, tenant_id, warehouse_id, material_id, kind, unit_code,
                    quantity_base, signed_quantity_effect, occurred_at, reason,
                    recorded_by_profile_id, version)
                VALUES (?, ?, ?, ?, 'ISSUE', 'KG', 0.5, -0.5, ?,
                    'Reconciliation provenance fixture', ?, 1)
                """, issueId, tenantId, warehouseId, materialId,
                Timestamp.from(Instant.parse("2027-03-01T08:00:00Z")), profileId);
        insertAllocation(harness, tenantId, issueId, sourceLotId, warehouseId, materialId);
        updateLot(harness, tenantId, sourceLotId, "-0.5");

        harness.jdbcTemplate().update("""
                INSERT INTO inventory_transactions (
                    id, tenant_id, warehouse_id, material_id, kind, unit_code,
                    quantity_base, signed_quantity_effect, procurement_effect_vnd,
                    occurred_at, reason, reversal_of, recorded_by_profile_id)
                VALUES (?, ?, ?, ?, 'REVERSAL', 'KG', 0.5, 0.5, 0, ?,
                    'Misallocated reversal fixture', ?, ?)
                """, reversalId, tenantId, warehouseId, materialId,
                Timestamp.from(Instant.parse("2027-03-01T08:01:00Z")), issueId, profileId);
        insertAllocation(harness, tenantId, reversalId, wrongLotId, warehouseId, materialId);
        updateLot(harness, tenantId, wrongLotId, "0.5");
        recomputeBalance(harness, tenantId, warehouseId, materialId);
    }

    private static UUID lotId(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            String batchCode) {
        return harness.jdbcTemplate().queryForObject("""
                SELECT id FROM stock_lots
                 WHERE tenant_id = ? AND warehouse_id = ? AND batch_code = ?
                """, UUID.class, tenantId, warehouseId, batchCode);
    }

    private static void insertAllocation(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID transactionId,
            UUID lotId,
            UUID warehouseId,
            UUID materialId) {
        harness.jdbcTemplate().update("""
                INSERT INTO inventory_transaction_lot_allocations (
                    id, tenant_id, transaction_id, stock_lot_id,
                    warehouse_id, material_id, quantity_base)
                VALUES (?, ?, ?, ?, ?, ?, 0.5)
                """, UUID.randomUUID(), tenantId, transactionId, lotId, warehouseId, materialId);
    }

    private static void updateLot(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID lotId,
            String delta) {
        harness.jdbcTemplate().update("""
                UPDATE stock_lots
                   SET available_quantity = available_quantity + ?::NUMERIC,
                       version = version + 1, updated_at = clock_timestamp()
                 WHERE tenant_id = ? AND id = ?
                """, delta, tenantId, lotId);
    }

    private static void recomputeBalance(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            UUID materialId) {
        harness.jdbcTemplate().update("""
                UPDATE stock_balances AS balance
                   SET quantity_on_hand = projection.quantity,
                       inventory_value_vnd = projection.value,
                       version = balance.version + 1,
                       updated_at = clock_timestamp()
                  FROM (
                       SELECT SUM(available_quantity) AS quantity,
                              SUM(ROUND(available_quantity * unit_cost_vnd, 2)) AS value
                         FROM stock_lots
                        WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                  ) AS projection
                 WHERE balance.tenant_id = ? AND balance.warehouse_id = ?
                   AND balance.material_id = ?
                """, tenantId, warehouseId, materialId, tenantId, warehouseId, materialId);
    }
}
