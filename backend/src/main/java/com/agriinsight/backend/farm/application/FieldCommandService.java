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
public class FieldCommandService {

    private final FieldService fields;
    private final CommandExecutionService commands;

    public FieldCommandService(FieldService fields, CommandExecutionService commands) {
        this.fields = Objects.requireNonNull(fields, "fields is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<FieldRecord> create(
            CommandExecutionRequest request,
            FieldCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        fields.requireFarmManagement(command.farmId());
        return commands.execute(
                request,
                () -> completion(201, fields.create(command)),
                target -> Optional.of(fields.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<FieldRecord> update(
            CommandExecutionRequest request,
            UUID fieldId,
            FieldCommands.Update command) {
        UUID requiredFieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        fields.getForManagement(requiredFieldId);
        return commands.execute(
                request,
                () -> completion(200, fields.update(requiredFieldId, command)),
                target -> Optional.of(fields.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<FieldRecord> deactivate(
            CommandExecutionRequest request,
            UUID fieldId,
            FieldCommands.Lifecycle command) {
        return lifecycle(request, fieldId, command, false);
    }

    public CommandExecutionResult<FieldRecord> reactivate(
            CommandExecutionRequest request,
            UUID fieldId,
            FieldCommands.Lifecycle command) {
        return lifecycle(request, fieldId, command, true);
    }

    private CommandExecutionResult<FieldRecord> lifecycle(
            CommandExecutionRequest request,
            UUID fieldId,
            FieldCommands.Lifecycle command,
            boolean active) {
        UUID requiredFieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        fields.getForManagement(requiredFieldId);
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active ? fields.reactivate(requiredFieldId, command)
                                : fields.deactivate(requiredFieldId, command)),
                target -> Optional.of(fields.getForManagement(target.resourceId())));
    }

    private CommandCompletion<FieldRecord> completion(int status, FieldRecord field) {
        return CommandCompletion.withRepresentation(
                status, "FIELD", field.id(), field.version(), field);
    }
}
