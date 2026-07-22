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
public class WarehouseCommandService {

    private final WarehouseService warehouses;
    private final CommandExecutionService commands;

    public WarehouseCommandService(
            WarehouseService warehouses,
            CommandExecutionService commands) {
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<WarehouseRecord> create(
            CommandExecutionRequest request,
            WarehouseCommands.Create command) {
        warehouses.requireTenantManagement();
        return commands.execute(
                request,
                () -> completion(201, warehouses.create(command)),
                target -> Optional.of(warehouses.getForTenantManagement(target.resourceId())));
    }

    public CommandExecutionResult<WarehouseRecord> update(
            CommandExecutionRequest request,
            UUID warehouseId,
            WarehouseCommands.Update command) {
        UUID target = Objects.requireNonNull(warehouseId, "warehouseId is required");
        warehouses.getForWarehouseManagement(target);
        return commands.execute(
                request,
                () -> completion(200, warehouses.update(target, command)),
                replayTarget -> Optional.of(
                        warehouses.getForWarehouseManagement(replayTarget.resourceId())));
    }

    public CommandExecutionResult<WarehouseRecord> deactivate(
            CommandExecutionRequest request,
            UUID warehouseId,
            WarehouseCommands.Lifecycle command) {
        return changeActive(request, warehouseId, command, false);
    }

    public CommandExecutionResult<WarehouseRecord> reactivate(
            CommandExecutionRequest request,
            UUID warehouseId,
            WarehouseCommands.Lifecycle command) {
        return changeActive(request, warehouseId, command, true);
    }

    private CommandExecutionResult<WarehouseRecord> changeActive(
            CommandExecutionRequest request,
            UUID warehouseId,
            WarehouseCommands.Lifecycle command,
            boolean active) {
        warehouses.requireTenantManagement();
        UUID target = Objects.requireNonNull(warehouseId, "warehouseId is required");
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active
                                ? warehouses.reactivate(target, command)
                                : warehouses.deactivate(target, command)),
                replayTarget -> Optional.of(
                        warehouses.getForTenantManagement(replayTarget.resourceId())));
    }

    private CommandCompletion<WarehouseRecord> completion(
            int status,
            WarehouseRecord warehouse) {
        return CommandCompletion.withRepresentation(
                status,
                "WAREHOUSE",
                warehouse.id(),
                warehouse.version(),
                warehouse);
    }
}
