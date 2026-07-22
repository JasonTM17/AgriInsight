package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Employee;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class EmployeeService {

    private final PermissionEvaluator permissions;
    private final EmployeeStore store;
    private final TenantAuditPublisher auditPublisher;

    public EmployeeService(
            PermissionEvaluator permissions,
            EmployeeStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public EmployeePage list(EmployeeQuery query) {
        ScopeContext scope = permissions.requireTenant(Permission.WORKFORCE_MANAGE);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public EmployeePage eligible(EmployeeQuery query) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.WORKFORCE_PICKER_READ, ScopeContext.Type.FARM);
        return store.findEligible(scope, Objects.requireNonNull(query, "query is required"));
    }

    public EmployeeRecord get(UUID employeeId) {
        ScopeContext scope = permissions.requireTenant(Permission.WORKFORCE_MANAGE);
        return requiredEmployee(scope, requiredId(employeeId));
    }

    public EmployeeRecord create(EmployeeCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        Employee employee = new Employee(
                UUID.randomUUID(), scope.tenantId(), command.code(),
                command.displayName(), command.jobTitle());
        EmployeeRecord created = store.create(scope, employee);
        publish(scope, TenantAuditEvent.Action.EMPLOYEE_CREATED, created, command.audit());
        return created;
    }

    public EmployeeRecord update(UUID employeeId, EmployeeCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        UUID requiredId = requiredId(employeeId);
        requiredEmployee(scope, requiredId);
        Optional<EmployeeRecord> updated = store.update(
                scope, requiredId, command.expectedVersion(), command);
        if (updated.isPresent()) {
            EmployeeRecord employee = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.EMPLOYEE_UPDATED, employee, command.audit());
            return employee;
        }
        return failedMutation(scope, requiredId, command.expectedVersion());
    }

    public EmployeeRecord deactivate(UUID employeeId, EmployeeCommands.Lifecycle command) {
        return changeActive(employeeId, command, false);
    }

    public EmployeeRecord reactivate(UUID employeeId, EmployeeCommands.Lifecycle command) {
        return changeActive(employeeId, command, true);
    }

    ScopeContext requireManagement() {
        return permissions.requireTenant(Permission.WORKFORCE_MANAGE);
    }

    EmployeeRecord getForManagement(UUID employeeId) {
        return requiredEmployee(requireManagement(), requiredId(employeeId));
    }

    private EmployeeRecord changeActive(
            UUID employeeId, EmployeeCommands.Lifecycle command, boolean active) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        UUID requiredId = requiredId(employeeId);
        requiredEmployee(scope, requiredId);
        Optional<EmployeeRecord> updated = store.updateActive(
                scope, requiredId, command.expectedVersion(), active);
        if (updated.isPresent()) {
            EmployeeRecord employee = updated.orElseThrow();
            publish(
                    scope,
                    active ? TenantAuditEvent.Action.EMPLOYEE_REACTIVATED
                            : TenantAuditEvent.Action.EMPLOYEE_DEACTIVATED,
                    employee,
                    command.audit());
            return employee;
        }
        EmployeeRecord current = requiredEmployee(scope, requiredId);
        if (current.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), current.version());
        }
        if (!active && current.active() && store.hasDeactivationBlockers(scope, requiredId)) {
            throw new ResourceStateConflictException(
                    "Employee has live field responsibilities or activity assignments");
        }
        throw new ResourceStateConflictException(
                active ? "Employee is already active" : "Employee is already inactive");
    }

    private EmployeeRecord failedMutation(ScopeContext scope, UUID employeeId, long expectedVersion) {
        EmployeeRecord current = requiredEmployee(scope, employeeId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        throw new ResourceStateConflictException("Employee update does not change state");
    }

    private EmployeeRecord requiredEmployee(ScopeContext scope, UUID employeeId) {
        return store.findById(scope, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            EmployeeRecord employee,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope, action, TenantAuditEvent.TargetType.EMPLOYEE,
                Optional.of(employee.id()), Optional.of(employee.code()),
                metadata.reasonCode(), metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "employeeId is required");
    }
}
