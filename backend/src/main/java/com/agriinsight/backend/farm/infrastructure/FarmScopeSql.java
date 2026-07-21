package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class FarmScopeSql {

    private FarmScopeSql() {
    }

    static void append(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope,
            UUID targetFarmId) {
        Objects.requireNonNull(sql, "sql is required");
        Objects.requireNonNull(parameters, "parameters are required");
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() == ScopeContext.Type.TENANT) {
            if (scope.resourceId().isPresent()) {
                throw new IllegalArgumentException("Tenant farm scope cannot target a resource");
            }
            return;
        }
        if (scope.type() != ScopeContext.Type.FARM) {
            throw new IllegalArgumentException("Farm store requires tenant or farm scope");
        }
        UUID scopedFarmId = scope.resourceId().orElse(null);
        if (targetFarmId != null
                && scopedFarmId != null
                && !scopedFarmId.equals(targetFarmId)) {
            throw new IllegalArgumentException("Farm scope cannot target another farm");
        }
        if (scopedFarmId != null) {
            sql.append(" AND farm.id = ?");
            parameters.add(scopedFarmId);
        }
        sql.append("""
                 AND EXISTS (
                       SELECT 1
                         FROM user_farm_assignments assignment
                        WHERE assignment.tenant_id = farm.tenant_id
                          AND assignment.user_profile_id = ?
                          AND assignment.farm_id = farm.id
                          AND assignment.revoked_at IS NULL
                 )
                """);
        parameters.add(scope.profileId());
    }
}
