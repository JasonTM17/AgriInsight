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
public class InventoryTransactionCommandService {

    private final InventoryTransactionService inventory;
    private final CommandExecutionService commands;

    public InventoryTransactionCommandService(
            InventoryTransactionService inventory,
            CommandExecutionService commands) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<InventoryTransactionRecord> post(
            CommandExecutionRequest request,
            InventoryTransactionCommands.Posting command) {
        inventory.requirePostingTarget(command);
        return commands.execute(
                request,
                () -> completion(201, inventory.post(command)),
                target -> Optional.of(inventory.getForReplay(target.resourceId())));
    }

    public CommandExecutionResult<InventoryTransactionRecord> reverse(
            CommandExecutionRequest request,
            UUID transactionId,
            InventoryTransactionCommands.Reversal command) {
        UUID target = Objects.requireNonNull(transactionId, "transactionId is required");
        inventory.requireReversalTarget(target, command);
        return commands.execute(
                request,
                () -> completion(201, inventory.reverse(target, command)),
                replayTarget -> Optional.of(inventory.getForReplay(replayTarget.resourceId())));
    }

    private CommandCompletion<InventoryTransactionRecord> completion(
            int status,
            InventoryTransactionRecord transaction) {
        return CommandCompletion.withRepresentation(
                status,
                "INVENTORY_TRANSACTION",
                transaction.id(),
                transaction.version(),
                transaction);
    }
}
