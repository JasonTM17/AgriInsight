package com.agriinsight.backend.farm.application;

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
public class FarmAssignmentCommandService {

    private final FarmAssignmentService assignments;
    private final CommandExecutionService commands;

    public FarmAssignmentCommandService(
            FarmAssignmentService assignments,
            CommandExecutionService commands) {
        this.assignments = Objects.requireNonNull(assignments, "assignments is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<FarmAssignmentRecord> grant(
            CommandExecutionRequest request,
            FarmAssignmentCommands.Grant command) {
        assignments.requireGrantTargets(command);
        return commands.execute(
                request,
                () -> completion(201, assignments.grant(command)),
                target -> Optional.of(assignments.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<FarmAssignmentRecord> revoke(
            CommandExecutionRequest request,
            UUID assignmentId,
            FarmAssignmentCommands.Revoke command) {
        UUID requiredId = Objects.requireNonNull(assignmentId, "assignmentId is required");
        assignments.getForManagement(requiredId);
        return commands.execute(
                request,
                () -> completion(200, assignments.revoke(requiredId, command)),
                target -> Optional.of(assignments.getForManagement(target.resourceId())));
    }

    private CommandCompletion<FarmAssignmentRecord> completion(
            int status,
            FarmAssignmentRecord assignment) {
        return CommandCompletion.withRepresentation(
                status,
                "FARM_ASSIGNMENT",
                assignment.id(),
                assignment.version(),
                assignment);
    }
}
