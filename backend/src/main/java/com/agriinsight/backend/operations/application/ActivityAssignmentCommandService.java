package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
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
public class ActivityAssignmentCommandService {

    private final ActivityAssignmentService assignments;
    private final CommandExecutionService commands;

    public ActivityAssignmentCommandService(
            ActivityAssignmentService assignments,
            CommandExecutionService commands) {
        this.assignments = Objects.requireNonNull(assignments, "assignments is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<ActivityAssignmentRecord> grant(
            CommandExecutionRequest request,
            UUID activityId,
            ActivityAssignmentCommands.Grant command) {
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        assignments.requireGrantTarget(requiredActivityId, command);
        return commands.execute(
                request,
                () -> completion(201, assignments.grant(requiredActivityId, command)),
                target -> Optional.of(assignments.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<ActivityAssignmentRecord> revoke(
            CommandExecutionRequest request,
            UUID activityId,
            UUID assignmentId,
            ActivityAssignmentCommands.Revoke command) {
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        UUID requiredAssignmentId = Objects.requireNonNull(assignmentId, "assignmentId is required");
        ActivityAssignmentRecord assignment = assignments.getForManagement(requiredAssignmentId);
        if (!assignment.activityId().equals(requiredActivityId)) {
            throw new ResourceNotFoundException("Activity assignment");
        }
        return commands.execute(
                request,
                () -> completion(200, assignments.revoke(requiredAssignmentId, command)),
                target -> Optional.of(assignments.getForManagement(target.resourceId())));
    }

    private CommandCompletion<ActivityAssignmentRecord> completion(
            int status,
            ActivityAssignmentRecord assignment) {
        return CommandCompletion.withRepresentation(
                status,
                "ACTIVITY_ASSIGNMENT",
                assignment.id(),
                assignment.version(),
                assignment);
    }
}
