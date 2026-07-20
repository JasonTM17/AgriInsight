package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantUserCommandService {

    private final TenantUserService tenantUsers;
    private final CommandExecutionService commands;

    public TenantUserCommandService(
            TenantUserService tenantUsers,
            CommandExecutionService commands) {
        this.tenantUsers = Objects.requireNonNull(tenantUsers, "tenantUsers is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
    }

    public CommandExecutionResult<TenantUserProfile> create(
            CommandExecutionRequest request,
            TenantUserCommands.Create command) {
        tenantUsers.requireUserManagement();
        return commands.execute(
                request,
                () -> {
                    ProvisionedTenantUser created = tenantUsers.create(command);
                    return CommandCompletion.withRepresentation(
                            201,
                            "USER_PROFILE",
                            created.profile().id(),
                            created.profile().version(),
                            created.profile());
                },
                target -> Optional.of(tenantUsers.get(target.resourceId())));
    }

    public CommandExecutionResult<TenantUserProfile> deactivate(
            CommandExecutionRequest request,
            UUID profileId,
            TenantUserCommands.Lifecycle command) {
        return changeActive(request, profileId, command, false);
    }

    public CommandExecutionResult<TenantUserProfile> reactivate(
            CommandExecutionRequest request,
            UUID profileId,
            TenantUserCommands.Lifecycle command) {
        return changeActive(request, profileId, command, true);
    }

    public CommandExecutionResult<ExternalIdentityReference> linkIdentity(
            CommandExecutionRequest request,
            UUID profileId,
            TenantUserCommands.LinkIdentity command) {
        tenantUsers.requireUserManagement();
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        return commands.execute(
                request,
                () -> {
                    ExternalIdentityReference linked = tenantUsers.linkIdentity(requiredProfileId, command);
                    return CommandCompletion.withRepresentation(
                            201,
                            "EXTERNAL_IDENTITY",
                            linked.id(),
                            linked.version(),
                            linked);
                },
                target -> {
                    tenantUsers.get(requiredProfileId);
                    return Optional.of(new ExternalIdentityReference(
                            target.resourceId(),
                            command.issuer(),
                            true,
                            target.resourceVersion()));
                });
    }

    public CommandExecutionResult<CommandTarget> unlinkIdentity(
            CommandExecutionRequest request,
            UUID profileId,
            UUID identityId,
            TenantAuditMetadata audit) {
        tenantUsers.requireUserManagement();
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        UUID requiredIdentityId = Objects.requireNonNull(identityId, "identityId is required");
        return commands.execute(
                request,
                () -> {
                    long version = tenantUsers.unlinkIdentity(requiredProfileId, requiredIdentityId, audit);
                    return CommandCompletion.withRepresentation(
                            200,
                            "EXTERNAL_IDENTITY",
                            requiredIdentityId,
                            version,
                            new CommandTarget("EXTERNAL_IDENTITY", requiredIdentityId, version));
                },
                target -> {
                    tenantUsers.get(requiredProfileId);
                    return Optional.of(target);
                });
    }

    private CommandExecutionResult<TenantUserProfile> changeActive(
            CommandExecutionRequest request,
            UUID profileId,
            TenantUserCommands.Lifecycle command,
            boolean active) {
        tenantUsers.requireUserManagement();
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        return commands.execute(
                request,
                () -> {
                    TenantUserProfile profile = active
                            ? tenantUsers.reactivate(requiredProfileId, command)
                            : tenantUsers.deactivate(requiredProfileId, command);
                    return CommandCompletion.withRepresentation(
                            200,
                            "USER_PROFILE",
                            profile.id(),
                            profile.version(),
                            profile);
                },
                target -> Optional.of(tenantUsers.get(target.resourceId())));
    }
}
