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
public class SeasonCommandService {

    private final SeasonService seasons;
    private final CommandExecutionService commands;

    public SeasonCommandService(SeasonService seasons, CommandExecutionService commands) {
        this.seasons = Objects.requireNonNull(seasons, "seasons is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<SeasonRecord> create(
            CommandExecutionRequest request,
            SeasonCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        seasons.requireFarmManagement(command.farmId());
        return commands.execute(
                request,
                () -> completion(201, seasons.create(command)),
                target -> Optional.of(seasons.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<SeasonRecord> update(
            CommandExecutionRequest request,
            UUID seasonId,
            SeasonCommands.Update command) {
        UUID requiredSeasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        seasons.getForManagement(requiredSeasonId);
        return commands.execute(
                request,
                () -> completion(200, seasons.update(requiredSeasonId, command)),
                target -> Optional.of(seasons.getForManagement(target.resourceId())));
    }

    public CommandExecutionResult<SeasonRecord> transition(
            CommandExecutionRequest request,
            UUID seasonId,
            SeasonCommands.Transition command) {
        UUID requiredSeasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        seasons.getForManagement(requiredSeasonId);
        return commands.execute(
                request,
                () -> completion(200, seasons.transition(requiredSeasonId, command)),
                target -> Optional.of(seasons.getForManagement(target.resourceId())));
    }

    private CommandCompletion<SeasonRecord> completion(int status, SeasonRecord season) {
        return CommandCompletion.withRepresentation(
                status,
                "SEASON",
                season.id(),
                season.version(),
                season);
    }
}
