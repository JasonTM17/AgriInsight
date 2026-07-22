package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.WarehouseRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresWarehouseLifecycleStore {

    private static final String DEACTIVATION_BLOCKER_PREDICATE = """
            EXISTS (
                SELECT 1 FROM user_warehouse_assignments AS assignment
                 WHERE assignment.tenant_id = warehouse.tenant_id
                   AND assignment.warehouse_id = warehouse.id
                   AND assignment.revoked_at IS NULL
            ) OR EXISTS (
                SELECT 1 FROM stock_balances AS balance
                 WHERE balance.tenant_id = warehouse.tenant_id
                   AND balance.warehouse_id = warehouse.id
                   AND balance.quantity_on_hand > 0
            ) OR EXISTS (
                SELECT 1 FROM stock_lots AS lot
                 WHERE lot.tenant_id = warehouse.tenant_id
                   AND lot.warehouse_id = warehouse.id
                   AND lot.available_quantity > 0
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresWarehouseLifecycleStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<WarehouseRecord> updateActive(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion,
            boolean active) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID target = Objects.requireNonNull(warehouseId, "warehouseId is required");
        requireVersion(expectedVersion);
        if (!lockWarehouse(tenantScope, target)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE warehouses AS warehouse
                   SET active = ?, version = warehouse.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE warehouse.tenant_id = ?
                   AND warehouse.id = ?
                   AND warehouse.version = ?
                   AND warehouse.active <> ?
                """);
        if (!active) {
            sql.append(" AND NOT (").append(DEACTIVATION_BLOCKER_PREDICATE).append(')');
        }
        sql.append(" RETURNING ").append(WarehouseRowMapping.RETURNING_COLUMNS);
        return WarehouseRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), WarehouseRowMapping.MAPPER,
                active, tenantScope.tenantId(), target, expectedVersion, active));
    }

    boolean hasDeactivationBlockers(ScopeContext scope, UUID warehouseId) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID target = Objects.requireNonNull(warehouseId, "warehouseId is required");
        String sql = "SELECT (" + DEACTIVATION_BLOCKER_PREDICATE + ") AS blocked "
                + "FROM warehouses AS warehouse WHERE warehouse.tenant_id = ? AND warehouse.id = ?";
        List<Boolean> rows = jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getBoolean("blocked"),
                tenantScope.tenantId(), target);
        return WarehouseRowMapping.exactlyOneOrEmpty(rows).orElse(false);
    }

    private boolean lockWarehouse(ScopeContext scope, UUID warehouseId) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT warehouse.id
                  FROM warehouses AS warehouse
                 WHERE warehouse.tenant_id = ?
                   AND warehouse.id = ?
                   FOR UPDATE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), warehouseId);
        return WarehouseRowMapping.exactlyOneOrEmpty(rows).isPresent();
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Warehouse lifecycle requires tenant-wide scope");
        }
        return required;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
