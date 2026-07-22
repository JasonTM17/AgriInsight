package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ActivityScopeSql {

    private ActivityScopeSql() {
    }

    static void appendRead(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope) {
        Objects.requireNonNull(sql, "sql is required");
        Objects.requireNonNull(parameters, "parameters are required");
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() == ScopeContext.Type.TENANT) {
            if (required.resourceId().isPresent()) {
                throw new IllegalArgumentException("Tenant activity scope cannot target a resource");
            }
            return;
        }
        if (required.type() == ScopeContext.Type.FARM) {
            appendFarmManagerScope(sql, parameters, required);
            return;
        }
        if (required.type() != ScopeContext.Type.ACTIVITY) {
            throw new IllegalArgumentException("Activity store requires tenant, farm, or activity scope");
        }
        required.resourceId().ifPresent(activityId -> {
            sql.append(" AND activity.id = ?");
            parameters.add(activityId);
        });
        sql.append("""
                 AND (
                       EXISTS (
                           SELECT 1
                             FROM user_roles AS manager_role
                             JOIN user_farm_assignments AS farm_assignment
                               ON farm_assignment.tenant_id = manager_role.tenant_id
                              AND farm_assignment.user_profile_id = manager_role.user_profile_id
                              AND farm_assignment.revoked_at IS NULL
                            WHERE manager_role.tenant_id = activity.tenant_id
                              AND manager_role.user_profile_id = ?
                              AND manager_role.role_code = 'FARM_MANAGER'
                              AND manager_role.revoked_at IS NULL
                              AND farm_assignment.farm_id = activity.farm_id
                       ) OR EXISTS (
                           SELECT 1
                             FROM user_roles AS worker_role
                             JOIN user_profiles AS worker_profile
                               ON worker_profile.tenant_id = worker_role.tenant_id
                              AND worker_profile.id = worker_role.user_profile_id
                              AND worker_profile.active
                             JOIN activity_assignees AS activity_assignment
                               ON activity_assignment.tenant_id = worker_profile.tenant_id
                              AND activity_assignment.employee_id = worker_profile.employee_id
                              AND activity_assignment.revoked_at IS NULL
                            WHERE worker_role.tenant_id = activity.tenant_id
                              AND worker_role.user_profile_id = ?
                              AND worker_role.role_code = 'FIELD_WORKER'
                              AND worker_role.revoked_at IS NULL
                              AND activity_assignment.activity_id = activity.id
                       )
                 )
                """);
        parameters.add(required.profileId());
        parameters.add(required.profileId());
    }

    static ScopeContext requireWriteScope(ScopeContext scope, UUID farmId) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        UUID targetFarmId = Objects.requireNonNull(farmId, "farmId is required");
        if (required.type() == ScopeContext.Type.TENANT && required.resourceId().isEmpty()) {
            return required;
        }
        if (required.type() != ScopeContext.Type.FARM
                || required.resourceId().isEmpty()
                || !required.resourceId().orElseThrow().equals(targetFarmId)) {
            throw new IllegalArgumentException("Activity write requires tenant-wide or target farm scope");
        }
        return required;
    }

    static boolean farmVisible(JdbcTemplate jdbcTemplate, ScopeContext scope, UUID farmId) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        ScopeContext writeScope = requireWriteScope(scope, farmId);
        if (writeScope.type() == ScopeContext.Type.TENANT) {
            return exists(jdbcTemplate, """
                    SELECT farm.id FROM farms AS farm
                    WHERE farm.tenant_id = ? AND farm.id = ?
                    """, writeScope.tenantId(), farmId);
        }
        return exists(jdbcTemplate, managerFarmSql("SELECT farm.id"),
                writeScope.tenantId(), writeScope.profileId(), farmId);
    }

    static boolean lockWriteAuthorization(
            JdbcTemplate jdbcTemplate,
            ScopeContext scope,
            UUID farmId) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        ScopeContext writeScope = requireWriteScope(scope, farmId);
        if (writeScope.type() == ScopeContext.Type.TENANT) {
            return true;
        }
        List<UUID> assignments = jdbcTemplate.query("""
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
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                writeScope.tenantId(), writeScope.profileId(), farmId);
        if (assignments.size() > 1) {
            throw new IllegalStateException("Activity write authorization returned multiple assignments");
        }
        return !assignments.isEmpty();
    }

    private static void appendFarmManagerScope(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope) {
        scope.resourceId().ifPresent(farmId -> {
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
        parameters.add(scope.profileId());
    }

    private static String managerFarmSql(String selection) {
        return selection + """
                  FROM farms AS farm
                 WHERE farm.tenant_id = ?
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
                   AND farm.id = ?
                """;
    }

    private static boolean exists(JdbcTemplate jdbcTemplate, String sql, Object... parameters) {
        return !jdbcTemplate.query(sql, (result, rowNumber) -> 1, parameters).isEmpty();
    }
}
