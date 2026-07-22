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
public class SupplierCommandService {

    private final SupplierService suppliers;
    private final CommandExecutionService commands;

    public SupplierCommandService(
            SupplierService suppliers,
            CommandExecutionService commands) {
        this.suppliers = Objects.requireNonNull(suppliers, "suppliers is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<SupplierRecord> create(
            CommandExecutionRequest request,
            SupplierCommands.Create command) {
        suppliers.requireTenantManagement();
        return commands.execute(
                request,
                () -> completion(201, suppliers.create(command)),
                target -> Optional.of(suppliers.getForTenantManagement(target.resourceId())));
    }

    public CommandExecutionResult<SupplierRecord> update(
            CommandExecutionRequest request,
            UUID supplierId,
            SupplierCommands.Update command) {
        suppliers.requireTenantManagement();
        UUID target = Objects.requireNonNull(supplierId, "supplierId is required");
        return commands.execute(
                request,
                () -> completion(200, suppliers.update(target, command)),
                replayTarget -> Optional.of(
                        suppliers.getForTenantManagement(replayTarget.resourceId())));
    }

    public CommandExecutionResult<SupplierRecord> deactivate(
            CommandExecutionRequest request,
            UUID supplierId,
            SupplierCommands.Lifecycle command) {
        return changeActive(request, supplierId, command, false);
    }

    public CommandExecutionResult<SupplierRecord> reactivate(
            CommandExecutionRequest request,
            UUID supplierId,
            SupplierCommands.Lifecycle command) {
        return changeActive(request, supplierId, command, true);
    }

    private CommandExecutionResult<SupplierRecord> changeActive(
            CommandExecutionRequest request,
            UUID supplierId,
            SupplierCommands.Lifecycle command,
            boolean active) {
        suppliers.requireTenantManagement();
        UUID target = Objects.requireNonNull(supplierId, "supplierId is required");
        return commands.execute(
                request,
                () -> completion(
                        200,
                        active
                                ? suppliers.reactivate(target, command)
                                : suppliers.deactivate(target, command)),
                replayTarget -> Optional.of(
                        suppliers.getForTenantManagement(replayTarget.resourceId())));
    }

    private CommandCompletion<SupplierRecord> completion(int status, SupplierRecord supplier) {
        return CommandCompletion.withRepresentation(
                status,
                "SUPPLIER",
                supplier.id(),
                supplier.version(),
                supplier);
    }
}
