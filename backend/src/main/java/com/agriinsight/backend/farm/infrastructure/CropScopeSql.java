package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;

final class CropScopeSql {

    private CropScopeSql() {
    }

    static void append(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope) {
        Objects.requireNonNull(sql, "sql is required");
        Objects.requireNonNull(parameters, "parameters are required");
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() == ScopeContext.Type.TENANT) {
            if (required.resourceId().isPresent()) {
                throw new IllegalArgumentException("Tenant crop scope cannot target a resource");
            }
            return;
        }
        if (required.type() != ScopeContext.Type.FARM) {
            throw new IllegalArgumentException("Crop reads require tenant or farm scope");
        }
        sql.append("""
                 AND EXISTS (
                       SELECT 1
                         FROM user_farm_assignments AS assignment
                        WHERE assignment.tenant_id = crop.tenant_id
                          AND assignment.user_profile_id = ?
                          AND assignment.revoked_at IS NULL
                """);
        parameters.add(required.profileId());
        required.resourceId().ifPresent(farmId -> {
            sql.append(" AND assignment.farm_id = ?");
            parameters.add(farmId);
        });
        sql.append(')');
    }
}
