package com.agriinsight.backend.inventory.application;

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
public class WarehouseAssignmentCommandService {

    private final WarehouseAssignmentService assignments;
    private final CommandExecutionService commands;

    public WarehouseAssignmentCommandService(
            WarehouseAssignmentService assignments,
            CommandExecutionService commands) {
        this.assignments = Objects.requireNonNull(assignments, "assignments is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<WarehouseAssignmentRecord> grant(
            CommandExecutionRequest request,
            WarehouseAssignmentCommands.Grant command) {
        assignments.requireGrantTargets(command);
        return commands.execute(
                request,
                () -> completion(201, assignments.grant(command)),
                target -> Optional.of(assignments.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<WarehouseAssignmentRecord> revoke(
            CommandExecutionRequest request,
            UUID assignmentId,
            WarehouseAssignmentCommands.Revoke command) {
        UUID target = Objects.requireNonNull(assignmentId, "assignmentId is required");
        assignments.getForManagement(target);
        return commands.execute(
                request,
                () -> completion(200, assignments.revoke(target, command)),
                replayTarget -> Optional.of(
                        assignments.getForManagement(replayTarget.resourceId())));
    }

    private CommandCompletion<WarehouseAssignmentRecord> completion(
            int status,
            WarehouseAssignmentRecord assignment) {
        return CommandCompletion.withRepresentation(
                status,
                "WAREHOUSE_ASSIGNMENT",
                assignment.id(),
                assignment.version(),
                assignment);
    }
}
