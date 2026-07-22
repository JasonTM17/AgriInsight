package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.EmployeeCommands;
import com.agriinsight.backend.operations.application.EmployeeRecord;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresEmployeeMutationStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresEmployeeMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<EmployeeRecord> update(
            ScopeContext scope,
            UUID employeeId,
            long expectedVersion,
            EmployeeCommands.Update command) {
        ScopeContext tenantScope = requireTenantScope(scope);
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        List<ColumnValue> columns = columns(command);
        StringBuilder sql = new StringBuilder("UPDATE employees AS employee SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = employee.version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE employee.tenant_id = ?
                   AND employee.id = ?
                   AND employee.version = ?
                   AND (
                """);
        parameters.add(tenantScope.tenantId());
        parameters.add(Objects.requireNonNull(employeeId, "employeeId is required"));
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(") RETURNING ").append(EmployeeRowMapping.RETURNING_COLUMNS);
        return EmployeeRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), EmployeeRowMapping.MAPPER, parameters.toArray()));
    }

    private List<ColumnValue> columns(EmployeeCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.displayName().ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        command.jobTitle().ifPresent(value -> columns.add(
                new ColumnValue("job_title", nullable(value.orElse(null)))));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one employee value must be provided");
        }
        return columns;
    }

    private void appendAssignments(
            StringBuilder sql, List<Object> parameters, List<ColumnValue> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            ColumnValue column = columns.get(index);
            sql.append(column.name()).append(" = ?");
            parameters.add(column.value());
        }
    }

    private void appendDifferencePredicate(
            StringBuilder sql, List<Object> parameters, List<ColumnValue> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            ColumnValue column = columns.get(index);
            sql.append("employee.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Employee mutation requires tenant-wide scope");
        }
        return required;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ColumnValue(String name, Object value) {
    }
}
