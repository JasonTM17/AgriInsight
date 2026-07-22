package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityLogAccess;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresActivityLogAccessResolver {

    private final JdbcTemplate jdbcTemplate;

    PostgresActivityLogAccessResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<ActivityLogAccess> resolve(ScopeContext scope, UUID activityId) {
        ScopeContext required = ActivityLogScope.require(scope, activityId);
        if (required.type() == ScopeContext.Type.TENANT) {
            return exactlyOneFarm("""
                    SELECT farm_id FROM activities
                     WHERE tenant_id = ? AND id = ?
                     FOR SHARE
                    """, required.tenantId(), activityId)
                    .map(farmId -> new ActivityLogAccess(farmId, true, Optional.empty()));
        }
        Optional<UUID> managerFarm = exactlyOneFarm("""
                SELECT activity.farm_id
                  FROM activities AS activity
                  JOIN user_roles AS manager_role
                    ON manager_role.tenant_id = activity.tenant_id
                   AND manager_role.user_profile_id = ?
                   AND manager_role.role_code = 'FARM_MANAGER'
                   AND manager_role.revoked_at IS NULL
                  JOIN user_farm_assignments AS farm_assignment
                    ON farm_assignment.tenant_id = activity.tenant_id
                   AND farm_assignment.user_profile_id = manager_role.user_profile_id
                   AND farm_assignment.farm_id = activity.farm_id
                   AND farm_assignment.revoked_at IS NULL
                 WHERE activity.tenant_id = ? AND activity.id = ?
                 FOR SHARE OF activity, manager_role, farm_assignment
                """, required.profileId(), required.tenantId(), activityId);
        if (managerFarm.isPresent()) {
            return managerFarm.map(farmId ->
                    new ActivityLogAccess(farmId, true, Optional.empty()));
        }
        List<ActivityLogAccess> workers = jdbcTemplate.query("""
                SELECT activity.farm_id, employee.id AS employee_id
                  FROM activities AS activity
                  JOIN user_roles AS worker_role
                    ON worker_role.tenant_id = activity.tenant_id
                   AND worker_role.user_profile_id = ?
                   AND worker_role.role_code = 'FIELD_WORKER'
                   AND worker_role.revoked_at IS NULL
                  JOIN user_profiles AS profile
                    ON profile.tenant_id = worker_role.tenant_id
                   AND profile.id = worker_role.user_profile_id
                   AND profile.active
                  JOIN employees AS employee
                    ON employee.tenant_id = profile.tenant_id
                   AND employee.id = profile.employee_id
                   AND employee.active
                  JOIN activity_assignees AS assignment
                    ON assignment.tenant_id = activity.tenant_id
                   AND assignment.activity_id = activity.id
                   AND assignment.employee_id = employee.id
                   AND assignment.revoked_at IS NULL
                 WHERE activity.tenant_id = ? AND activity.id = ?
                 FOR SHARE OF activity, worker_role, profile, employee, assignment
                """, (result, rowNumber) -> new ActivityLogAccess(
                        result.getObject("farm_id", UUID.class), false,
                        Optional.of(result.getObject("employee_id", UUID.class))),
                required.profileId(), required.tenantId(), activityId);
        if (workers.size() > 1) {
            throw new IllegalStateException("Activity worker access returned multiple assignments");
        }
        return workers.stream().findFirst();
    }

    private Optional<UUID> exactlyOneFarm(String sql, Object... parameters) {
        List<UUID> farms = jdbcTemplate.query(
                sql, (result, rowNumber) -> result.getObject("farm_id", UUID.class), parameters);
        if (farms.size() > 1) {
            throw new IllegalStateException("Activity log access returned multiple farms");
        }
        return farms.stream().findFirst();
    }
}
