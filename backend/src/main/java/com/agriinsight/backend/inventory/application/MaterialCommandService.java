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
public class MaterialCommandService {

    private final MaterialService materials;
    private final CommandExecutionService commands;

    public MaterialCommandService(
            MaterialService materials,
            CommandExecutionService commands) {
        this.materials = Objects.requireNonNull(materials, "materials is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<MaterialRecord> create(
            CommandExecutionRequest request,
            MaterialCommands.Create command) {
        materials.requireTenantManagement();
        return commands.execute(
                request,
                () -> completion(201, materials.create(command)),
                target -> Optional.of(materials.getForTenantManagement(target.resourceId())));
    }

    public CommandExecutionResult<MaterialRecord> update(
            CommandExecutionRequest request,
            UUID materialId,
            MaterialCommands.Update command) {
        materials.requireTenantManagement();
        UUID target = Objects.requireNonNull(materialId, "materialId is required");
        return commands.execute(
                request,
                () -> completion(200, materials.update(target, command)),
                replayTarget -> Optional.of(
                        materials.getForTenantManagement(replayTarget.resourceId())));
    }

    public CommandExecutionResult<MaterialRecord> deactivate(
            CommandExecutionRequest request,
            UUID materialId,
            MaterialCommands.Lifecycle command) {
        return changeActive(request, materialId, command, false);
    }

    public CommandExecutionResult<MaterialRecord> reactivate(
            CommandExecutionRequest request,
            UUID materialId,
            MaterialCommands.Lifecycle command) {
        return changeActive(request, materialId, command, true);
    }

    private CommandExecutionResult<MaterialRecord> changeActive(
            CommandExecutionRequest request,
            UUID materialId,
            MaterialCommands.Lifecycle command,
            boolean active) {
        materials.requireTenantManagement();
        UUID target = Objects.requireNonNull(materialId, "materialId is required");
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active
                                ? materials.reactivate(target, command)
                                : materials.deactivate(target, command)),
                replayTarget -> Optional.of(
                        materials.getForTenantManagement(replayTarget.resourceId())));
    }

    private CommandCompletion<MaterialRecord> completion(int status, MaterialRecord material) {
        return CommandCompletion.withRepresentation(
                status,
                "MATERIAL",
                material.id(),
                material.version(),
                material);
    }
}
