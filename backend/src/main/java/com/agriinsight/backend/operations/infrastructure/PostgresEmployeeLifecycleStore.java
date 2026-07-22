package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.EmployeeRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresEmployeeLifecycleStore {

    private static final String LIVE_RESPONSIBILITY_PREDICATE = """
            EXISTS (
                SELECT 1 FROM fields AS field
                 WHERE field.tenant_id = employee.tenant_id
                   AND field.responsible_employee_id = employee.id
                   AND field.active)
            OR EXISTS (
                SELECT 1 FROM activity_assignees AS assignment
                 WHERE assignment.tenant_id = employee.tenant_id
                   AND assignment.employee_id = employee.id
                   AND assignment.revoked_at IS NULL)
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresEmployeeLifecycleStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<EmployeeRecord> updateActive(
            ScopeContext scope,
            UUID employeeId,
            long expectedVersion,
            boolean active) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID requiredId = Objects.requireNonNull(employeeId, "employeeId is required");
        requireVersion(expectedVersion);
        if (!lockEmployee(tenantScope, requiredId)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE employees AS employee
                   SET active = ?, version = employee.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE employee.tenant_id = ?
                   AND employee.id = ?
                   AND employee.version = ?
                   AND employee.active <> ?
                """);
        if (!active) {
            sql.append(" AND NOT (").append(LIVE_RESPONSIBILITY_PREDICATE).append(')');
        }
        sql.append(" RETURNING ").append(EmployeeRowMapping.RETURNING_COLUMNS);
        return EmployeeRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), EmployeeRowMapping.MAPPER,
                active, tenantScope.tenantId(), requiredId, expectedVersion, active));
    }

    boolean hasDeactivationBlockers(ScopeContext scope, UUID employeeId) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID requiredId = Objects.requireNonNull(employeeId, "employeeId is required");
        String sql = "SELECT (" + LIVE_RESPONSIBILITY_PREDICATE + ") AS blocked "
                + "FROM employees AS employee WHERE employee.tenant_id = ? AND employee.id = ?";
        return EmployeeRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getBoolean("blocked"),
                tenantScope.tenantId(), requiredId)).orElse(false);
    }

    private boolean lockEmployee(ScopeContext scope, UUID employeeId) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT employee.id FROM employees AS employee
                 WHERE employee.tenant_id = ? AND employee.id = ?
                 FOR UPDATE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), employeeId);
        return EmployeeRowMapping.exactlyOneOrEmpty(rows).isPresent();
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Employee lifecycle requires tenant-wide scope");
        }
        return required;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
