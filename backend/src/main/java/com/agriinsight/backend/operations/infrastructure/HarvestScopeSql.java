package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class HarvestScopeSql {

    private HarvestScopeSql() {
    }

    static void appendRead(StringBuilder sql, List<Object> parameters, ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() == ScopeContext.Type.TENANT && required.resourceId().isEmpty()) {
            return;
        }
        if (required.type() != ScopeContext.Type.FARM) {
            throw new IllegalArgumentException("Harvest store requires tenant or farm scope");
        }
        required.resourceId().ifPresent(farmId -> {
            sql.append(" AND farm.id = ?");
            parameters.add(farmId);
        });
        sql.append("""
                 AND EXISTS (
                       SELECT 1
                         FROM user_roles AS manager_role
                         JOIN user_farm_assignments AS farm_assignment
                           ON farm_assignment.tenant_id = manager_role.tenant_id
                          AND farm_assignment.user_profile_id = manager_role.user_profile_id
                          AND farm_assignment.revoked_at IS NULL
                        WHERE manager_role.tenant_id = farm.tenant_id
                          AND manager_role.user_profile_id = ?
                          AND manager_role.role_code = 'FARM_MANAGER'
                          AND manager_role.revoked_at IS NULL
                          AND farm_assignment.farm_id = farm.id
                 )
                """);
        parameters.add(required.profileId());
    }

    static ScopeContext requireWriteScope(ScopeContext scope, UUID farmId) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        UUID target = Objects.requireNonNull(farmId, "farmId is required");
        if (required.type() == ScopeContext.Type.TENANT && required.resourceId().isEmpty()) {
            return required;
        }
        if (required.type() != ScopeContext.Type.FARM
                || required.resourceId().isEmpty()
                || !required.resourceId().orElseThrow().equals(target)) {
            throw new IllegalArgumentException("Harvest write requires tenant-wide or target farm scope");
        }
        return required;
    }

    static boolean farmVisible(JdbcTemplate jdbcTemplate, ScopeContext scope, UUID farmId) {
        ScopeContext required = requireWriteScope(scope, farmId);
        StringBuilder sql = new StringBuilder(
                "SELECT farm.id FROM farms AS farm WHERE farm.tenant_id = ? AND farm.id = ?");
        List<Object> parameters = new java.util.ArrayList<>(List.of(required.tenantId(), farmId));
        appendRead(sql, parameters, required);
        return !jdbcTemplate.query(
                sql.toString(), (result, rowNumber) -> result.getObject("id", UUID.class),
                parameters.toArray()).isEmpty();
    }

    static boolean lockWriteAuthorization(
            JdbcTemplate jdbcTemplate, ScopeContext scope, UUID farmId) {
        ScopeContext required = requireWriteScope(scope, farmId);
        if (required.type() == ScopeContext.Type.TENANT) {
            return true;
        }
        List<UUID> rows = jdbcTemplate.query("""
                SELECT farm_assignment.id
                  FROM user_roles AS manager_role
                  JOIN user_farm_assignments AS farm_assignment
                    ON farm_assignment.tenant_id = manager_role.tenant_id
                   AND farm_assignment.user_profile_id = manager_role.user_profile_id
                   AND farm_assignment.revoked_at IS NULL
                 WHERE manager_role.tenant_id = ?
                   AND manager_role.user_profile_id = ?
                   AND manager_role.role_code = 'FARM_MANAGER'
                   AND manager_role.revoked_at IS NULL
                   AND farm_assignment.farm_id = ?
                 FOR SHARE OF manager_role, farm_assignment
                """, (result, rowNumber) -> result.getObject("id", UUID.class),
                required.tenantId(), required.profileId(), farmId);
        if (rows.size() > 1) {
            throw new IllegalStateException("Harvest write authorization returned multiple assignments");
        }
        return !rows.isEmpty();
    }
}
