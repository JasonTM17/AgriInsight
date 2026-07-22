package com.agriinsight.backend.cost.application;

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
public class OperatingCostCommandService {

    private final OperatingCostService costs;
    private final CommandExecutionService commands;

    public OperatingCostCommandService(
            OperatingCostService costs, CommandExecutionService commands) {
        this.costs = Objects.requireNonNull(costs, "costs is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<OperatingCostRecord> post(
            CommandExecutionRequest request, CostCommands.Post command) {
        costs.requirePostTarget(command);
        return commands.execute(
                request,
                () -> completion(costs.post(command, request.idempotencyKey().digest())),
                target -> Optional.of(costs.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<CostCorrectionRecord> correct(
            CommandExecutionRequest request,
            UUID originalEntryId,
            CostCommands.Correct command) {
        UUID originalId = Objects.requireNonNull(
                originalEntryId, "originalEntryId is required");
        costs.requireCorrectionAccess(originalId, command);
        return commands.execute(
                request,
                () -> completion(costs.correct(
                        originalId, command, request.idempotencyKey().digest())),
                target -> Optional.of(costs.getCorrectionForReplay(target.resourceId())));
    }

    private CommandCompletion<OperatingCostRecord> completion(OperatingCostRecord entry) {
        return CommandCompletion.withRepresentation(
                201, "OPERATING_COST_ENTRY", entry.id(), entry.version(), entry);
    }

    private CommandCompletion<CostCorrectionRecord> completion(CostCorrectionRecord correction) {
        return CommandCompletion.withRepresentation(
                201,
                "OPERATING_COST_ENTRY",
                correction.replacement().id(),
                correction.replacement().version(),
                correction);
    }
}
