package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;

final class InventoryCatalogScopeSql {

    private InventoryCatalogScopeSql() {
    }

    static ScopeContext require(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() == ScopeContext.Type.TENANT && required.resourceId().isEmpty()) {
            return required;
        }
        if (required.type() == ScopeContext.Type.WAREHOUSE && required.resourceId().isEmpty()) {
            return required;
        }
        throw new IllegalArgumentException(
                "Inventory catalog requires tenant-wide or warehouse-list scope");
    }

    static ScopeContext requireTenantWrite(ScopeContext scope, String resourceName) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException(resourceName + " mutation requires tenant-wide scope");
        }
        return required;
    }

    static void append(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope,
            String catalogAlias) {
        Objects.requireNonNull(sql, "sql is required");
        Objects.requireNonNull(parameters, "parameters are required");
        ScopeContext required = require(scope);
        String alias = requireAlias(catalogAlias);
        if (required.type() == ScopeContext.Type.TENANT) {
            return;
        }
        sql.append("""
                 AND EXISTS (
                       SELECT 1
                         FROM user_warehouse_assignments AS assignment
                        WHERE assignment.tenant_id = %s.tenant_id
                          AND assignment.user_profile_id = ?
                          AND assignment.revoked_at IS NULL
                 )
                """.formatted(alias));
        parameters.add(required.profileId());
    }

    private static String requireAlias(String alias) {
        if (!"material".equals(alias) && !"supplier".equals(alias)) {
            throw new IllegalArgumentException("Unsupported inventory catalog alias");
        }
        return alias;
    }
}
