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
public class FarmCommandService {

    private final FarmService farms;
    private final CommandExecutionService commands;

    public FarmCommandService(FarmService farms, CommandExecutionService commands) {
        this.farms = Objects.requireNonNull(farms, "farms is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<FarmRecord> create(
            CommandExecutionRequest request,
            FarmCommands.Create command) {
        farms.requireTenantManagement();
        return commands.execute(
                request,
                () -> completion(201, farms.create(command)),
                target -> Optional.of(farms.getForTenantManagement(target.resourceId())));
    }

    public CommandExecutionResult<FarmRecord> update(
            CommandExecutionRequest request,
            UUID farmId,
            FarmCommands.Update command) {
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        farms.getForFarmManagement(requiredFarmId);
        return commands.execute(
                request,
                () -> completion(200, farms.update(requiredFarmId, command)),
                target -> Optional.of(farms.getForFarmManagement(target.resourceId())));
    }

    public CommandExecutionResult<FarmRecord> deactivate(
            CommandExecutionRequest request,
            UUID farmId,
            FarmCommands.Lifecycle command) {
        return changeActive(request, farmId, command, false);
    }

    public CommandExecutionResult<FarmRecord> reactivate(
            CommandExecutionRequest request,
            UUID farmId,
            FarmCommands.Lifecycle command) {
        return changeActive(request, farmId, command, true);
    }

    private CommandExecutionResult<FarmRecord> changeActive(
            CommandExecutionRequest request,
            UUID farmId,
            FarmCommands.Lifecycle command,
            boolean active) {
        farms.requireTenantManagement();
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active
                                ? farms.reactivate(requiredFarmId, command)
                                : farms.deactivate(requiredFarmId, command)),
                target -> Optional.of(farms.getForTenantManagement(target.resourceId())));
    }

    private CommandCompletion<FarmRecord> completion(int status, FarmRecord farm) {
        return CommandCompletion.withRepresentation(
                status,
                "FARM",
                farm.id(),
                farm.version(),
                farm);
    }
}
