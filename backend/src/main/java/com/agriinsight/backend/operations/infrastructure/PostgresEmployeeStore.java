package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.EmployeeCommands;
import com.agriinsight.backend.operations.application.EmployeePage;
import com.agriinsight.backend.operations.application.EmployeeQuery;
import com.agriinsight.backend.operations.application.EmployeeRecord;
import com.agriinsight.backend.operations.application.EmployeeStore;
import com.agriinsight.backend.operations.domain.Employee;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresEmployeeStore implements EmployeeStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresEmployeeMutationStore mutations;
    private final PostgresEmployeeLifecycleStore lifecycle;

    public PostgresEmployeeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresEmployeeMutationStore(jdbcTemplate);
        this.lifecycle = new PostgresEmployeeLifecycleStore(jdbcTemplate);
    }

    @Override
    public EmployeePage findAll(ScopeContext scope, EmployeeQuery query) {
        return findPage(requireTenantScope(scope), query, false);
    }

    @Override
    public EmployeePage findEligible(ScopeContext scope, EmployeeQuery query) {
        return findPage(requirePickerScope(scope), query, true);
    }

    private EmployeePage findPage(ScopeContext scope, EmployeeQuery query, boolean eligibleOnly) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE employee.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(scope.tenantId());
        if (eligibleOnly) {
            sql.append(" AND employee.active");
        } else {
            query.active().ifPresent(active -> addFilter(sql, parameters, "employee.active", active));
        }
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(employee.code)) > 0")
                    .append(" OR position(lower(?) in lower(employee.display_name)) > 0)");
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(employee.display_name), employee.code, employee.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<EmployeeRecord> rows = jdbcTemplate.query(
                sql.toString(), EmployeeRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<EmployeeRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new EmployeePage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<EmployeeRecord> findById(ScopeContext scope, UUID employeeId) {
        ScopeContext tenantScope = requireTenantScope(scope);
        return EmployeeRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                baseSelect().append(" WHERE employee.tenant_id = ? AND employee.id = ?").toString(),
                EmployeeRowMapping.MAPPER,
                tenantScope.tenantId(), Objects.requireNonNull(employeeId, "employeeId is required")));
    }

    @Override
    public EmployeeRecord create(ScopeContext scope, Employee employee) {
        ScopeContext tenantScope = requireTenantScope(scope);
        Objects.requireNonNull(employee, "employee is required");
        if (!tenantScope.tenantId().equals(employee.tenantId())) {
            throw new IllegalArgumentException("Employee cannot switch tenants");
        }
        return EmployeeRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO employees (id, tenant_id, code, display_name, job_title)
                VALUES (?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(EmployeeRowMapping.RETURNING_COLUMNS),
                EmployeeRowMapping.MAPPER,
                employee.id(), employee.tenantId(), employee.code(), employee.displayName(),
                nullable(employee.jobTitle().orElse(null))))
                .orElseThrow(() -> new IllegalStateException("Employee was not created"));
    }

    @Override
    public Optional<EmployeeRecord> update(
            ScopeContext scope, UUID employeeId, long expectedVersion, EmployeeCommands.Update command) {
        return mutations.update(scope, employeeId, expectedVersion, command);
    }

    @Override
    public Optional<EmployeeRecord> updateActive(
            ScopeContext scope, UUID employeeId, long expectedVersion, boolean active) {
        return lifecycle.updateActive(scope, employeeId, expectedVersion, active);
    }

    @Override
    public boolean hasDeactivationBlockers(ScopeContext scope, UUID employeeId) {
        return lifecycle.hasDeactivationBlockers(scope, employeeId);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(EmployeeRowMapping.SELECT_COLUMNS)
                .append(" FROM employees AS employee");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Employee access requires tenant-wide scope");
        }
        return required;
    }

    private ScopeContext requirePickerScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.resourceId().isPresent()
                || (required.type() != ScopeContext.Type.TENANT
                    && required.type() != ScopeContext.Type.FARM)) {
            throw new IllegalArgumentException("Employee picker requires tenant or farm-list scope");
        }
        return required;
    }
}
