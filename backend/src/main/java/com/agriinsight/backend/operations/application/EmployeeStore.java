package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Employee;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeStore {

    EmployeePage findAll(ScopeContext scope, EmployeeQuery query);

    EmployeePage findEligible(ScopeContext scope, EmployeeQuery query);

    Optional<EmployeeRecord> findById(ScopeContext scope, UUID employeeId);

    EmployeeRecord create(ScopeContext scope, Employee employee);

    Optional<EmployeeRecord> update(
            ScopeContext scope,
            UUID employeeId,
            long expectedVersion,
            EmployeeCommands.Update command);

    Optional<EmployeeRecord> updateActive(
            ScopeContext scope,
            UUID employeeId,
            long expectedVersion,
            boolean active);

    boolean hasDeactivationBlockers(ScopeContext scope, UUID employeeId);
}
