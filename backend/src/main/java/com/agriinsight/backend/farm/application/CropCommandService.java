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
public class CropCommandService {

    private final CropService crops;
    private final CommandExecutionService commands;

    public CropCommandService(CropService crops, CommandExecutionService commands) {
        this.crops = Objects.requireNonNull(crops, "crops is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<CropRecord> create(
            CommandExecutionRequest request,
            CropCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        crops.requireTenantManagement();
        return commands.execute(
                request,
                () -> completion(201, crops.create(command)),
                target -> Optional.of(crops.getForTenantManagement(target.resourceId())));
    }

    public CommandExecutionResult<CropRecord> update(
            CommandExecutionRequest request,
            UUID cropId,
            CropCommands.Update command) {
        UUID requiredCropId = Objects.requireNonNull(cropId, "cropId is required");
        crops.getForTenantManagement(requiredCropId);
        return commands.execute(
                request,
                () -> completion(200, crops.update(requiredCropId, command)),
                target -> Optional.of(crops.getForTenantManagement(target.resourceId())));
    }

    public CommandExecutionResult<CropRecord> deactivate(
            CommandExecutionRequest request,
            UUID cropId,
            CropCommands.Lifecycle command) {
        return lifecycle(request, cropId, command, false);
    }

    public CommandExecutionResult<CropRecord> reactivate(
            CommandExecutionRequest request,
            UUID cropId,
            CropCommands.Lifecycle command) {
        return lifecycle(request, cropId, command, true);
    }

    private CommandExecutionResult<CropRecord> lifecycle(
            CommandExecutionRequest request,
            UUID cropId,
            CropCommands.Lifecycle command,
            boolean active) {
        UUID requiredCropId = Objects.requireNonNull(cropId, "cropId is required");
        crops.getForTenantManagement(requiredCropId);
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active ? crops.reactivate(requiredCropId, command)
                                : crops.deactivate(requiredCropId, command)),
                target -> Optional.of(crops.getForTenantManagement(target.resourceId())));
    }

    private CommandCompletion<CropRecord> completion(int status, CropRecord crop) {
        return CommandCompletion.withRepresentation(
                status, "CROP", crop.id(), crop.version(), crop);
    }
}
