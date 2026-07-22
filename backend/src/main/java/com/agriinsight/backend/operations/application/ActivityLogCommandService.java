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
public class ActivityLogCommandService {

    private final ActivityLogService logs;
    private final CommandExecutionService commands;

    public ActivityLogCommandService(
            ActivityLogService logs,
            CommandExecutionService commands) {
        this.logs = Objects.requireNonNull(logs, "logs is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<ActivityLogRecord> append(
            CommandExecutionRequest request,
            UUID activityId,
            ActivityLogCommands.Append command) {
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(command, "command is required");
        logs.requireAppendTarget(requiredActivityId, command.employeeId());
        return commands.execute(
                request,
                () -> completion(201, logs.append(requiredActivityId, command)),
                target -> Optional.of(logs.getForReplay(requiredActivityId, target.resourceId())));
    }

    public CommandExecutionResult<ActivityLogRecord> correct(
            CommandExecutionRequest request,
            UUID activityId,
            UUID logId,
            ActivityLogCommands.Correct command) {
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        UUID requiredLogId = Objects.requireNonNull(logId, "logId is required");
        Objects.requireNonNull(command, "command is required");
        logs.requireCorrectionTarget(requiredActivityId, requiredLogId);
        return commands.execute(
                request,
                () -> completion(201, logs.correct(requiredActivityId, requiredLogId, command)),
                target -> Optional.of(logs.getForReplay(requiredActivityId, target.resourceId())));
    }

    private CommandCompletion<ActivityLogRecord> completion(
            int status,
            ActivityLogRecord log) {
        return CommandCompletion.withRepresentation(
                status, "ACTIVITY_LOG", log.id(), log.version(), log);
    }
}
