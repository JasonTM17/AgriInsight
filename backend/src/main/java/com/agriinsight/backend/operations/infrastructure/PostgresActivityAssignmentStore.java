package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityAssignmentRecord;
import com.agriinsight.backend.operations.application.ActivityAssignmentStore;
import com.agriinsight.backend.operations.domain.ActivityAssignment;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresActivityAssignmentStore implements ActivityAssignmentStore {

    private static final String COLUMNS = "assignment.id, assignment.tenant_id, assignment.activity_id, "
            + "assignment.employee_id, assignment.revoked_at, assignment.version";

    private static final RowMapper<ActivityAssignmentRecord> MAPPER = (result, rowNumber) -> {
        Timestamp revokedAt = result.getTimestamp("revoked_at");
        return new ActivityAssignmentRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("activity_id", UUID.class),
                result.getObject("employee_id", UUID.class),
                revokedAt == null ? Optional.empty() : Optional.of(revokedAt.toInstant()),
                result.getLong("version"));
    };

    private final JdbcTemplate jdbcTemplate;

    public PostgresActivityAssignmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public Optional<ActivityAssignmentRecord> findById(ScopeContext scope, UUID assignmentId) {
        ScopeContext required = requireLookupScope(scope);
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM activity_assignees AS assignment "
                        + "JOIN activities AS activity ON activity.tenant_id = assignment.tenant_id "
                        + "AND activity.id = assignment.activity_id "
                        + "WHERE assignment.tenant_id = ? AND assignment.id = ?" + farmPredicate(required),
                MAPPER, parameters(required, assignmentId)));
    }

    @Override
    public Optional<ActivityAssignmentRecord> findActive(
            ScopeContext scope,
            UUID activityId,
            UUID employeeId) {
        ScopeContext required = requireScope(scope);
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM activity_assignees AS assignment "
                        + "JOIN activities AS activity ON activity.tenant_id = assignment.tenant_id "
                        + "AND activity.id = assignment.activity_id "
                        + "WHERE assignment.tenant_id = ? AND assignment.activity_id = ? "
                        + "AND assignment.employee_id = ? AND assignment.revoked_at IS NULL"
                        + farmPredicate(required),
                MAPPER, parameters(required, activityId, employeeId)));
    }

    @Override
    public boolean activeEmployeeExists(ScopeContext scope, UUID employeeId) {
        ScopeContext required = requireScope(scope);
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employees WHERE tenant_id = ? AND id = ? AND active",
                Long.class, required.tenantId(), Objects.requireNonNull(employeeId, "employeeId is required"));
        return count != null && count == 1;
    }

    @Override
    public Optional<ActivityAssignmentRecord> create(
            ScopeContext scope,
            ActivityAssignment assignment) {
        ScopeContext required = requireScope(scope);
        ActivityAssignment value = Objects.requireNonNull(assignment, "assignment is required");
        if (!required.tenantId().equals(value.tenantId())) {
            throw new IllegalArgumentException("Activity assignment cannot switch tenants");
        }
        if (!lockLiveActivity(required, value.activityId())) {
            return Optional.empty();
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO activity_assignees (id, tenant_id, activity_id, employee_id)
                SELECT ?, activity.tenant_id, activity.id, employee.id
                  FROM activities AS activity
                  JOIN employees AS employee
                    ON employee.tenant_id = activity.tenant_id
                   AND employee.id = ?
                   AND employee.active
                 WHERE activity.tenant_id = ?
                   AND activity.id = ?
                   AND activity.farm_id = ?
                RETURNING id, tenant_id, activity_id, employee_id, revoked_at, version
                """, MAPPER,
                value.id(), value.employeeId(), value.tenantId(), value.activityId(),
                required.resourceId().orElseThrow()));
    }

    @Override
    public Optional<ActivityAssignmentRecord> revoke(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion) {
        ScopeContext required = requireScope(scope);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        String sql = """
                UPDATE activity_assignees AS assignment
                   SET revoked_at = CURRENT_TIMESTAMP,
                       version = assignment.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                  FROM activities AS activity
                 WHERE activity.tenant_id = assignment.tenant_id
                   AND activity.id = assignment.activity_id
                   AND assignment.tenant_id = ?
                   AND assignment.id = ?
                   AND assignment.version = ?
                   AND assignment.revoked_at IS NULL
                """ + farmPredicate(required) + " RETURNING " + COLUMNS;
        return exactlyOneOrEmpty(jdbcTemplate.query(
                sql, MAPPER, parameters(required, assignmentId, expectedVersion)));
    }

    private ScopeContext requireScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.FARM || required.resourceId().isEmpty()) {
            throw new IllegalArgumentException("Activity assignment store requires target farm scope");
        }
        return required;
    }

    private boolean lockLiveActivity(ScopeContext scope, UUID activityId) {
        return !jdbcTemplate.query("""
                SELECT id FROM activities
                 WHERE tenant_id = ?
                   AND id = ?
                   AND farm_id = ?
                   AND status IN ('PLANNED', 'STARTED')
                 FOR SHARE
                """, (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), Objects.requireNonNull(activityId, "activityId is required"),
                scope.resourceId().orElseThrow()).isEmpty();
    }

    private ScopeContext requireLookupScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if ((required.type() == ScopeContext.Type.ACTIVITY
                || required.type() == ScopeContext.Type.TENANT)
                && required.resourceId().isEmpty()) {
            return required;
        }
        return requireScope(required);
    }

    private String farmPredicate(ScopeContext scope) {
        return scope.type() == ScopeContext.Type.FARM ? " AND activity.farm_id = ?" : "";
    }

    private Object[] parameters(ScopeContext scope, Object... values) {
        int farmParameter = scope.type() == ScopeContext.Type.FARM ? 1 : 0;
        Object[] parameters = new Object[values.length + 1 + farmParameter];
        parameters[0] = scope.tenantId();
        System.arraycopy(values, 0, parameters, 1, values.length);
        if (farmParameter == 1) {
            parameters[parameters.length - 1] = scope.resourceId().orElseThrow();
        }
        return parameters;
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Activity assignment query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
