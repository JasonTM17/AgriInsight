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
public class HarvestCommandService {

    private final HarvestService harvests;
    private final CommandExecutionService commands;

    public HarvestCommandService(HarvestService harvests, CommandExecutionService commands) {
        this.harvests = Objects.requireNonNull(harvests, "harvests is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<HarvestRecord> post(
            CommandExecutionRequest request, HarvestCommands.Post command) {
        Objects.requireNonNull(command, "command is required");
        harvests.requirePostTarget(command);
        return commands.execute(
                request, () -> completion(harvests.post(command)),
                target -> Optional.of(harvests.getForReplay(target.resourceId())));
    }

    public CommandExecutionResult<HarvestRecord> correct(
            CommandExecutionRequest request, UUID harvestId, HarvestCommands.Correct command) {
        UUID requiredId = Objects.requireNonNull(harvestId, "harvestId is required");
        Objects.requireNonNull(command, "command is required");
        harvests.getForManagement(requiredId);
        return commands.execute(
                request, () -> completion(harvests.correct(requiredId, command)),
                target -> Optional.of(harvests.getForReplay(target.resourceId())));
    }

    private CommandCompletion<HarvestRecord> completion(HarvestRecord harvest) {
        return CommandCompletion.withRepresentation(
                201, "HARVEST", harvest.id(), harvest.version(), harvest);
    }
}
