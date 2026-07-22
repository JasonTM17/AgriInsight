package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class InventoryPostingTargetLocks {

    private final JdbcTemplate jdbcTemplate;

    InventoryPostingTargetLocks(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    boolean available(ScopeContext scope, InventoryTransactionCommands.Posting command) {
        Objects.requireNonNull(command, "command is required");
        return selectUnit(scope, command, false).isPresent();
    }

    CanonicalUnit lockPosting(
            ScopeContext scope, InventoryTransactionCommands.Posting command) {
        Objects.requireNonNull(command, "command is required");
        if (!WarehouseScopeSql.lockWriteAuthorization(
                jdbcTemplate, scope, command.warehouseId())) {
            throw new ResourceNotFoundException("Active inventory posting target");
        }
        return selectUnit(scope, command, true)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active inventory posting target"));
    }

    void lockWarehouseAccess(ScopeContext scope, UUID warehouseId) {
        ScopeContext required = WarehouseScopeSql.requireWriteScope(scope, warehouseId);
        if (!WarehouseScopeSql.lockWriteAuthorization(jdbcTemplate, required, warehouseId)) {
            throw new ResourceNotFoundException("Inventory transaction");
        }
        List<UUID> rows = jdbcTemplate.query("""
                SELECT warehouse.id
                  FROM warehouses AS warehouse
                 WHERE warehouse.tenant_id = ? AND warehouse.id = ? AND warehouse.active
                 FOR SHARE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                required.tenantId(), warehouseId);
        if (rows.size() != 1) {
            throw new ResourceNotFoundException("Inventory transaction");
        }
    }

    private Optional<CanonicalUnit> selectUnit(
            ScopeContext scope,
            InventoryTransactionCommands.Posting command,
            boolean lock) {
        ScopeContext required = WarehouseScopeSql.requireWriteScope(
                scope, command.warehouseId());
        boolean receipt = command instanceof InventoryTransactionCommands.Receipt;
        StringBuilder sql = new StringBuilder("""
                SELECT material.base_unit
                  FROM warehouses AS warehouse
                  JOIN materials AS material
                    ON material.tenant_id = warehouse.tenant_id AND material.id = ?
                """);
        if (receipt) {
            sql.append("""
                      JOIN suppliers AS supplier
                        ON supplier.tenant_id = warehouse.tenant_id AND supplier.id = ?
                    """);
        }
        sql.append("""
                 WHERE warehouse.tenant_id = ? AND warehouse.id = ?
                   AND warehouse.active AND material.active
                """);
        if (receipt) {
            sql.append(" AND supplier.active");
        }
        List<Object> parameters = new ArrayList<>();
        parameters.add(command.materialId());
        if (command instanceof InventoryTransactionCommands.Receipt receiptCommand) {
            parameters.add(receiptCommand.supplierId());
        }
        parameters.add(required.tenantId());
        parameters.add(command.warehouseId());
        WarehouseScopeSql.append(sql, parameters, required, command.warehouseId());
        if (lock) {
            sql.append(receipt
                    ? " FOR SHARE OF warehouse, material, supplier"
                    : " FOR SHARE OF warehouse, material");
        }
        List<CanonicalUnit> units = jdbcTemplate.query(
                sql.toString(),
                (result, rowNumber) -> CanonicalUnit.valueOf(result.getString("base_unit")),
                parameters.toArray());
        if (units.size() > 1) {
            throw new IllegalStateException("Inventory posting target returned multiple rows");
        }
        return units.stream().findFirst();
    }
}
