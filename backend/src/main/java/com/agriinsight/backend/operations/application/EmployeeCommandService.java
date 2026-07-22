package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
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
public class EmployeeCommandService {

    private final EmployeeService employees;
    private final CommandExecutionService commands;

    public EmployeeCommandService(EmployeeService employees, CommandExecutionService commands) {
        this.employees = Objects.requireNonNull(employees, "employees is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<EmployeeRecord> create(
            CommandExecutionRequest request, EmployeeCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        employees.requireManagement();
        return commands.execute(
                request,
                () -> completion(201, employees.create(command)),
                target -> Optional.of(employees.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<EmployeeRecord> update(
            CommandExecutionRequest request, UUID employeeId, EmployeeCommands.Update command) {
        UUID requiredId = Objects.requireNonNull(employeeId, "employeeId is required");
        employees.getForManagement(requiredId);
        return commands.execute(
                request,
                () -> completion(200, employees.update(requiredId, command)),
                target -> Optional.of(employees.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<EmployeeRecord> deactivate(
            CommandExecutionRequest request, UUID employeeId, EmployeeCommands.Lifecycle command) {
        return lifecycle(request, employeeId, command, false);
    }

    public CommandExecutionResult<EmployeeRecord> reactivate(
            CommandExecutionRequest request, UUID employeeId, EmployeeCommands.Lifecycle command) {
        return lifecycle(request, employeeId, command, true);
    }

    private CommandExecutionResult<EmployeeRecord> lifecycle(
            CommandExecutionRequest request,
            UUID employeeId,
            EmployeeCommands.Lifecycle command,
            boolean active) {
        UUID requiredId = Objects.requireNonNull(employeeId, "employeeId is required");
        employees.getForManagement(requiredId);
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active ? employees.reactivate(requiredId, command)
                                : employees.deactivate(requiredId, command)),
                target -> Optional.of(employees.getForManagement(target.resourceId())));
    }

    private CommandCompletion<EmployeeRecord> completion(int status, EmployeeRecord employee) {
        return CommandCompletion.withRepresentation(
                status, "EMPLOYEE", employee.id(), employee.version(), employee);
    }
}
