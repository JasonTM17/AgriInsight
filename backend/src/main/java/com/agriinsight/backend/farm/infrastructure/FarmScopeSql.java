package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class FarmScopeSql {

    private FarmScopeSql() {
    }

    static ScopeContext requireWriteScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        boolean tenantWide = required.type() == ScopeContext.Type.TENANT
                && required.resourceId().isEmpty();
        boolean targetedFarm = required.type() == ScopeContext.Type.FARM
                && required.resourceId().isPresent();
        if (!tenantWide && !targetedFarm) {
            throw new IllegalArgumentException("Farm write requires tenant-wide or target farm scope");
        }
        return required;
    }

    static ScopeContext requireWriteScope(ScopeContext scope, UUID targetFarmId) {
        ScopeContext required = requireWriteScope(scope);
        UUID target = Objects.requireNonNull(targetFarmId, "targetFarmId is required");
        if (required.type() == ScopeContext.Type.FARM
                && !required.resourceId().orElseThrow().equals(target)) {
            throw new IllegalArgumentException("Farm scope cannot target another farm");
        }
        return required;
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

    static boolean farmVisible(
            JdbcTemplate jdbcTemplate,
            ScopeContext scope,
            UUID farmId) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        ScopeContext requiredScope = Objects.requireNonNull(scope, "scope is required");
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        StringBuilder sql = new StringBuilder("""
                SELECT farm.id
                  FROM farms AS farm
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                requiredScope.tenantId(), requiredFarmId));
        append(sql, parameters, requiredScope, requiredFarmId);
        return !jdbcTemplate.query(
                sql.toString(),
                (result, rowNumber) -> result.getObject("id", UUID.class),
                parameters.toArray()).isEmpty();
    }
}
