package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryReconciliationReport;
import com.agriinsight.backend.inventory.application.InventoryReconciliationStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresInventoryReconciliationStore implements InventoryReconciliationStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresInventoryReconciliationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public InventoryReconciliationReport reconcile(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        Counts lots = reconcileLots(required);
        Counts balances = reconcileBalances(required);
        return new InventoryReconciliationReport(
                lots.checked(), lots.drifted(), balances.checked(), balances.drifted());
    }

    private Counts reconcileLots(ScopeContext scope) {
        StringBuilder sql = new StringBuilder("""
                WITH allocation_effect AS (
                    SELECT allocation.stock_lot_id,
                           SUM(CASE transaction.kind
                               WHEN 'ISSUE' THEN -allocation.quantity_base
                               WHEN 'REVERSAL' THEN allocation.quantity_base
                               ELSE 0 END) AS quantity
                      FROM inventory_transaction_lot_allocations AS allocation
                      JOIN inventory_transactions AS transaction
                        ON transaction.tenant_id = allocation.tenant_id
                       AND transaction.id = allocation.transaction_id
                     WHERE allocation.tenant_id = ?
                     GROUP BY allocation.stock_lot_id
                ), receipt_reversal AS (
                    SELECT reversal.reversal_of AS receipt_id,
                           SUM(reversal.quantity_base) AS quantity
                      FROM inventory_transactions AS reversal
                     WHERE reversal.tenant_id = ?
                       AND reversal.kind = 'REVERSAL'
                     GROUP BY reversal.reversal_of
                ), reconciled AS (
                    SELECT lot.available_quantity,
                           receipt.quantity_base
                               - COALESCE(receipt_reversal.quantity, 0)
                               + COALESCE(allocation_effect.quantity, 0) AS expected_quantity,
                           lot.received_quantity IS DISTINCT FROM receipt.quantity_base
                               OR lot.warehouse_id IS DISTINCT FROM receipt.warehouse_id
                               OR lot.material_id IS DISTINCT FROM receipt.material_id
                               OR lot.supplier_id IS DISTINCT FROM receipt.supplier_id
                               OR lot.batch_code IS DISTINCT FROM receipt.batch_code
                               OR lot.expiry_date IS DISTINCT FROM receipt.expiry_date
                               OR lot.received_at IS DISTINCT FROM receipt.occurred_at
                               OR lot.unit_code IS DISTINCT FROM receipt.unit_code
                               OR lot.unit_cost_vnd IS DISTINCT FROM receipt.unit_cost_vnd
                               AS metadata_drift
                      FROM stock_lots AS lot
                      JOIN warehouses AS warehouse
                        ON warehouse.tenant_id = lot.tenant_id
                       AND warehouse.id = lot.warehouse_id
                      JOIN inventory_transactions AS receipt
                        ON receipt.tenant_id = lot.tenant_id
                       AND receipt.id = lot.original_receipt_id
                      LEFT JOIN allocation_effect
                        ON allocation_effect.stock_lot_id = lot.id
                      LEFT JOIN receipt_reversal
                        ON receipt_reversal.receipt_id = receipt.id
                     WHERE lot.tenant_id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                scope.tenantId(), scope.tenantId(), scope.tenantId()));
        WarehouseScopeSql.append(sql, parameters, scope, null);
        sql.append("""
                )
                SELECT COUNT(*) AS checked,
                       COUNT(*) FILTER (WHERE available_quantity IS DISTINCT FROM expected_quantity
                           OR metadata_drift) AS drifted
                  FROM reconciled
                """);
        return counts(sql, parameters);
    }

    private Counts reconcileBalances(ScopeContext scope) {
        StringBuilder sql = new StringBuilder("""
                WITH lot_projection AS (
                    SELECT lot.tenant_id, lot.warehouse_id, lot.material_id,
                           SUM(lot.available_quantity)::NUMERIC(20, 4) AS quantity,
                           SUM(ROUND(lot.available_quantity * lot.unit_cost_vnd, 2))
                               ::NUMERIC(20, 2) AS inventory_value
                      FROM stock_lots AS lot
                     WHERE lot.tenant_id = ?
                     GROUP BY lot.tenant_id, lot.warehouse_id, lot.material_id
                ), reconciled AS (
                    SELECT balance.id,
                           balance.quantity_on_hand,
                           balance.inventory_value_vnd,
                           balance.unit_code,
                           COALESCE(projection.quantity, 0) AS expected_quantity,
                           COALESCE(projection.inventory_value, 0) AS expected_value,
                           material.base_unit
                      FROM stock_balances AS balance
                      FULL JOIN lot_projection AS projection
                        ON projection.tenant_id = balance.tenant_id
                       AND projection.warehouse_id = balance.warehouse_id
                       AND projection.material_id = balance.material_id
                      JOIN warehouses AS warehouse
                        ON warehouse.tenant_id = COALESCE(
                            balance.tenant_id, projection.tenant_id)
                       AND warehouse.id = COALESCE(
                            balance.warehouse_id, projection.warehouse_id)
                      JOIN materials AS material
                        ON material.tenant_id = warehouse.tenant_id
                       AND material.id = COALESCE(
                            balance.material_id, projection.material_id)
                     WHERE COALESCE(balance.tenant_id, projection.tenant_id) = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(scope.tenantId(), scope.tenantId()));
        WarehouseScopeSql.append(sql, parameters, scope, null);
        sql.append("""
                )
                SELECT COUNT(*) AS checked,
                       COUNT(*) FILTER (WHERE id IS NULL
                           OR quantity_on_hand IS DISTINCT FROM expected_quantity
                           OR inventory_value_vnd IS DISTINCT FROM expected_value
                           OR unit_code IS DISTINCT FROM base_unit) AS drifted
                  FROM reconciled
                """);
        return counts(sql, parameters);
    }

    private Counts counts(StringBuilder sql, List<Object> parameters) {
        Counts result = jdbcTemplate.queryForObject(
                sql.toString(),
                (row, rowNumber) -> new Counts(
                        row.getLong("checked"), row.getLong("drifted")),
                parameters.toArray());
        return Objects.requireNonNull(result, "Reconciliation counts are required");
    }

    private record Counts(long checked, long drifted) {
    }
}
