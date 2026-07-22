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
public class ActivityCommandService {

    private final ActivityService activities;
    private final CommandExecutionService commands;

    public ActivityCommandService(
            ActivityService activities,
            CommandExecutionService commands) {
        this.activities = Objects.requireNonNull(activities, "activities is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<ActivityRecord> create(
            CommandExecutionRequest request,
            ActivityCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        activities.requireFarmManagement(command.farmId());
        return commands.execute(
                request,
                () -> completion(201, activities.create(command)),
                target -> Optional.of(activities.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<ActivityRecord> update(
            CommandExecutionRequest request,
            UUID activityId,
            ActivityCommands.Update command) {
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        activities.getForManagement(requiredActivityId);
        return commands.execute(
                request,
                () -> completion(200, activities.update(requiredActivityId, command)),
                target -> Optional.of(activities.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<ActivityRecord> transition(
            CommandExecutionRequest request,
            UUID activityId,
            ActivityCommands.Transition command) {
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        activities.getForManagement(requiredActivityId);
        return commands.execute(
                request,
                () -> completion(200, activities.transition(requiredActivityId, command)),
                target -> Optional.of(activities.getForManagement(target.resourceId())));
    }

    private CommandCompletion<ActivityRecord> completion(int status, ActivityRecord activity) {
        return CommandCompletion.withRepresentation(
                status, "ACTIVITY", activity.id(), activity.version(), activity);
    }
}
