package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Role;
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
public class TenantRoleCommandService {

    private final TenantRoleAssignmentService assignments;
    private final CommandExecutionService commands;

    public TenantRoleCommandService(
            TenantRoleAssignmentService assignments,
            CommandExecutionService commands) {
        this.assignments = Objects.requireNonNull(assignments, "assignments is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<TenantRoleAssignment> grant(
            CommandExecutionRequest request,
            UUID profileId,
            TenantRoleAssignmentCommands.Grant command) {
        assignments.requireRoleManagement();
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        return commands.execute(
                request,
                () -> {
                    TenantRoleAssignment assignment = assignments.grant(requiredProfileId, command);
                    return CommandCompletion.withRepresentation(
                            200,
                            "USER_ROLE",
                            assignment.id(),
                            assignment.version(),
                            assignment);
                },
                target -> Optional.of(assignments.get(requiredProfileId, command.role())));
    }

    public CommandExecutionResult<TenantRoleAssignment> revoke(
            CommandExecutionRequest request,
            UUID profileId,
            Role role,
            TenantRoleAssignmentCommands.Revoke command) {
        assignments.requireRoleManagement();
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        Role requiredRole = Objects.requireNonNull(role, "role is required");
        return commands.execute(
                request,
                () -> {
                    TenantRoleAssignment assignment = assignments.revoke(requiredProfileId, requiredRole, command);
                    return CommandCompletion.withRepresentation(
                            200,
                            "USER_ROLE",
                            assignment.id(),
                            assignment.version(),
                            assignment);
                },
                target -> Optional.of(assignments.get(requiredProfileId, requiredRole)));
    }
}
