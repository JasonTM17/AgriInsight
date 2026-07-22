package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WarehouseScopeSql {

    private WarehouseScopeSql() {
    }

    static ScopeContext requireWriteScope(ScopeContext scope, UUID targetWarehouseId) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        UUID target = Objects.requireNonNull(targetWarehouseId, "targetWarehouseId is required");
        boolean tenantWide = required.type() == ScopeContext.Type.TENANT
                && required.resourceId().isEmpty();
        boolean targetedWarehouse = required.type() == ScopeContext.Type.WAREHOUSE
                && required.resourceId().filter(target::equals).isPresent();
        if (!tenantWide && !targetedWarehouse) {
            throw new IllegalArgumentException(
                    "Warehouse write requires tenant-wide or target warehouse scope");
        }
        return required;
    }

    static void append(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope,
            UUID targetWarehouseId) {
        Objects.requireNonNull(sql, "sql is required");
        Objects.requireNonNull(parameters, "parameters are required");
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() == ScopeContext.Type.TENANT) {
            if (required.resourceId().isPresent()) {
                throw new IllegalArgumentException("Tenant warehouse scope cannot target a resource");
            }
            return;
        }
        if (required.type() != ScopeContext.Type.WAREHOUSE) {
            throw new IllegalArgumentException("Warehouse store requires tenant or warehouse scope");
        }
        UUID scopedWarehouseId = required.resourceId().orElse(null);
        if (targetWarehouseId != null
                && scopedWarehouseId != null
                && !scopedWarehouseId.equals(targetWarehouseId)) {
            throw new IllegalArgumentException("Warehouse scope cannot target another warehouse");
        }
        if (scopedWarehouseId != null) {
            sql.append(" AND warehouse.id = ?");
            parameters.add(scopedWarehouseId);
        }
        sql.append("""
                 AND EXISTS (
                       SELECT 1
                         FROM user_warehouse_assignments AS assignment
                        WHERE assignment.tenant_id = warehouse.tenant_id
                          AND assignment.user_profile_id = ?
                          AND assignment.warehouse_id = warehouse.id
                          AND assignment.revoked_at IS NULL
                 )
                """);
        parameters.add(required.profileId());
    }

    static boolean lockWriteAuthorization(
            JdbcTemplate jdbcTemplate,
            ScopeContext scope,
            UUID warehouseId) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        ScopeContext required = requireWriteScope(scope, warehouseId);
        if (required.type() == ScopeContext.Type.TENANT) {
            return true;
        }
        List<UUID> rows = jdbcTemplate.query("""
                SELECT assignment.id
                  FROM user_warehouse_assignments AS assignment
                 WHERE assignment.tenant_id = ?
                   AND assignment.user_profile_id = ?
                   AND assignment.warehouse_id = ?
                   AND assignment.revoked_at IS NULL
                   FOR SHARE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                required.tenantId(), required.profileId(), warehouseId);
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Warehouse write authorization returned multiple assignments");
        }
        return !rows.isEmpty();
    }

}
