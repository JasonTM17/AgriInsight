package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryTransactionReconciliation {

    private final JdbcTemplate jdbcTemplate;

    PostgresInventoryTransactionReconciliation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    InventoryReconciliationCounts reconcile(ScopeContext scope) {
        StringBuilder sql = new StringBuilder("""
                WITH allocation_totals AS (
                    SELECT allocation.transaction_id,
                           SUM(allocation.quantity_base) AS quantity
                      FROM inventory_transaction_lot_allocations AS allocation
                     WHERE allocation.tenant_id = ?
                     GROUP BY allocation.transaction_id
                ), allocation_by_lot AS (
                    SELECT allocation.transaction_id, allocation.stock_lot_id,
                           SUM(allocation.quantity_base) AS quantity
                      FROM inventory_transaction_lot_allocations AS allocation
                     WHERE allocation.tenant_id = ?
                     GROUP BY allocation.transaction_id, allocation.stock_lot_id
                ), issue_reversal_lot_totals AS (
                    SELECT reversal.reversal_of, allocation.stock_lot_id,
                           SUM(allocation.quantity_base) AS quantity
                      FROM inventory_transactions AS reversal
                      JOIN inventory_transaction_lot_allocations AS allocation
                        ON allocation.tenant_id = reversal.tenant_id
                       AND allocation.transaction_id = reversal.id
                     WHERE reversal.tenant_id = ? AND reversal.kind = 'REVERSAL'
                     GROUP BY reversal.reversal_of, allocation.stock_lot_id
                ), receipt_lot_counts AS (
                    SELECT lot.original_receipt_id, COUNT(*) AS lot_count
                      FROM stock_lots AS lot
                     WHERE lot.tenant_id = ?
                     GROUP BY lot.original_receipt_id
                ), reversal_totals AS (
                    SELECT reversal.reversal_of,
                           SUM(reversal.quantity_base) AS quantity,
                           SUM(reversal.procurement_effect_vnd) AS procurement_effect,
                           COUNT(*) AS reversal_count
                      FROM inventory_transactions AS reversal
                     WHERE reversal.tenant_id = ? AND reversal.kind = 'REVERSAL'
                     GROUP BY reversal.reversal_of
                ), reconciled AS (
                    SELECT transaction.id,
                           CASE transaction.kind
                             WHEN 'RECEIPT' THEN
                               COALESCE(receipt_lot_counts.lot_count, 0) <> 1
                               OR COALESCE(allocation_totals.quantity, 0) <> 0
                               OR COALESCE(reversal_totals.quantity, 0)
                                   > transaction.quantity_base
                               OR COALESCE(reversal_totals.procurement_effect, 0)
                                   IS DISTINCT FROM -ROUND(
                                       COALESCE(reversal_totals.quantity, 0)
                                       * transaction.unit_cost_vnd, 2)
                               OR transaction.version IS DISTINCT FROM
                                   COALESCE(reversal_totals.reversal_count, 0)
                             WHEN 'ISSUE' THEN
                               COALESCE(allocation_totals.quantity, 0)
                                   IS DISTINCT FROM transaction.quantity_base
                               OR COALESCE(reversal_totals.quantity, 0)
                                   > transaction.quantity_base
                               OR transaction.version IS DISTINCT FROM
                                   COALESCE(reversal_totals.reversal_count, 0)
                             WHEN 'REVERSAL' THEN
                               original.id IS NULL
                               OR original.kind = 'REVERSAL'
                               OR transaction.warehouse_id IS DISTINCT FROM original.warehouse_id
                               OR transaction.material_id IS DISTINCT FROM original.material_id
                               OR transaction.unit_code IS DISTINCT FROM original.unit_code
                               OR transaction.signed_quantity_effect IS DISTINCT FROM
                                   CASE original.kind
                                     WHEN 'RECEIPT' THEN -transaction.quantity_base
                                     WHEN 'ISSUE' THEN transaction.quantity_base
                                   END
                               OR CASE original.kind
                                    WHEN 'RECEIPT' THEN
                                      COALESCE(allocation_totals.quantity, 0) <> 0
                                      OR transaction.unit_cost_vnd
                                          IS DISTINCT FROM original.unit_cost_vnd
                                      OR transaction.procurement_effect_vnd > 0
                                      OR transaction.supplier_id
                                          IS DISTINCT FROM original.supplier_id
                                      OR transaction.batch_code
                                          IS DISTINCT FROM original.batch_code
                                      OR transaction.expiry_date
                                          IS DISTINCT FROM original.expiry_date
                                    WHEN 'ISSUE' THEN
                                      COALESCE(allocation_totals.quantity, 0)
                                          IS DISTINCT FROM transaction.quantity_base
                                      OR transaction.unit_cost_vnd IS NOT NULL
                                      OR transaction.procurement_effect_vnd <> 0
                                      OR transaction.supplier_id IS NOT NULL
                                      OR transaction.batch_code IS NOT NULL
                                      OR transaction.expiry_date IS NOT NULL
                                      OR EXISTS (
                                          SELECT 1
                                            FROM allocation_by_lot AS restored
                                            LEFT JOIN allocation_by_lot AS issued
                                              ON issued.transaction_id = original.id
                                             AND issued.stock_lot_id = restored.stock_lot_id
                                            LEFT JOIN issue_reversal_lot_totals AS cumulative
                                              ON cumulative.reversal_of = original.id
                                             AND cumulative.stock_lot_id = restored.stock_lot_id
                                           WHERE restored.transaction_id = transaction.id
                                             AND (issued.stock_lot_id IS NULL
                                               OR cumulative.quantity > issued.quantity)
                                      )
                                  END
                           END AS drifted
                      FROM inventory_transactions AS transaction
                      JOIN warehouses AS warehouse
                        ON warehouse.tenant_id = transaction.tenant_id
                       AND warehouse.id = transaction.warehouse_id
                      LEFT JOIN inventory_transactions AS original
                        ON original.tenant_id = transaction.tenant_id
                       AND original.id = transaction.reversal_of
                      LEFT JOIN allocation_totals
                        ON allocation_totals.transaction_id = transaction.id
                      LEFT JOIN receipt_lot_counts
                        ON receipt_lot_counts.original_receipt_id = transaction.id
                      LEFT JOIN reversal_totals
                        ON reversal_totals.reversal_of = transaction.id
                     WHERE transaction.tenant_id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                scope.tenantId(), scope.tenantId(), scope.tenantId(),
                scope.tenantId(), scope.tenantId(), scope.tenantId()));
        WarehouseScopeSql.append(sql, parameters, scope, null);
        sql.append("""
                )
                SELECT COUNT(*) AS checked,
                       COUNT(*) FILTER (WHERE drifted) AS drifted
                  FROM reconciled
                """);
        InventoryReconciliationCounts result = jdbcTemplate.queryForObject(
                sql.toString(),
                (row, rowNumber) -> new InventoryReconciliationCounts(
                        row.getLong("checked"), row.getLong("drifted")),
                parameters.toArray());
        return Objects.requireNonNull(result, "Reconciliation counts are required");
    }
}
