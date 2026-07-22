package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryBalanceProjection {

    private final JdbcTemplate jdbcTemplate;

    PostgresInventoryBalanceProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    void lock(
            ScopeContext scope,
            UUID warehouseId,
            UUID materialId,
            CanonicalUnit unit) {
        jdbcTemplate.update("""
                INSERT INTO stock_balances (
                    id, tenant_id, warehouse_id, material_id, unit_code)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, warehouse_id, material_id) DO NOTHING
                """, UUID.randomUUID(), scope.tenantId(), warehouseId, materialId, unit.name());
        List<CanonicalUnit> units = jdbcTemplate.query("""
                SELECT balance.unit_code
                  FROM stock_balances AS balance
                 WHERE balance.tenant_id = ?
                   AND balance.warehouse_id = ?
                   AND balance.material_id = ?
                 FOR UPDATE
                """,
                (result, rowNumber) -> CanonicalUnit.valueOf(result.getString("unit_code")),
                scope.tenantId(), warehouseId, materialId);
        if (units.size() != 1 || units.getFirst() != unit) {
            throw new IllegalStateException("Inventory balance unit does not match material unit");
        }
    }

    void recompute(ScopeContext scope, UUID warehouseId, UUID materialId) {
        int updated = jdbcTemplate.update("""
                UPDATE stock_balances AS balance
                   SET quantity_on_hand = projection.quantity_on_hand,
                       inventory_value_vnd = projection.inventory_value_vnd,
                       version = balance.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                  FROM (
                        SELECT COALESCE(SUM(lot.available_quantity), 0)::NUMERIC(20, 4)
                                   AS quantity_on_hand,
                               COALESCE(SUM(ROUND(
                                   lot.available_quantity * lot.unit_cost_vnd, 2)), 0)
                                   ::NUMERIC(20, 2) AS inventory_value_vnd
                          FROM stock_lots AS lot
                         WHERE lot.tenant_id = ?
                           AND lot.warehouse_id = ?
                           AND lot.material_id = ?
                  ) AS projection
                 WHERE balance.tenant_id = ?
                   AND balance.warehouse_id = ?
                   AND balance.material_id = ?
                """, scope.tenantId(), warehouseId, materialId,
                scope.tenantId(), warehouseId, materialId);
        if (updated != 1) {
            throw new IllegalStateException("Inventory balance projection was not updated");
        }
    }
}
