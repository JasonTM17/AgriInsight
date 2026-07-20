package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAdministratorGuard;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.identity.domain.ExternalIdentity;
import com.agriinsight.backend.identity.domain.UserProfile;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
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
public class TenantUserService {

    private final PermissionEvaluator permissionEvaluator;
    private final TenantUserStore store;
    private final ConfiguredIdentityProvider identityProvider;
    private final TenantAdministratorGuard administratorGuard;
    private final TenantAuditPublisher auditPublisher;

    public TenantUserService(
            PermissionEvaluator permissionEvaluator,
            TenantUserStore store,
            ConfiguredIdentityProvider identityProvider,
            TenantAdministratorGuard administratorGuard,
            TenantAuditPublisher auditPublisher) {
        this.permissionEvaluator = Objects.requireNonNull(permissionEvaluator, "permissionEvaluator is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider is required");
        this.administratorGuard = Objects.requireNonNull(administratorGuard, "administratorGuard is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public TenantUserPage list(TenantUserQuery query) {
        ScopeContext scope = requireUserManagement();
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public TenantUserProfile get(UUID profileId) {
        ScopeContext scope = requireUserManagement();
        return store.findById(scope, requiredId(profileId, "profileId"))
                .orElseThrow(() -> new ResourceNotFoundException("Tenant user"));
    }

    public ProvisionedTenantUser create(TenantUserCommands.Create command) {
        ScopeContext scope = requireUserManagement();
        Objects.requireNonNull(command, "command is required");
        identityProvider.requireConfiguredIssuer(command.issuer());

        UserProfile profile = new UserProfile(
                UUID.randomUUID(),
                scope.tenantId(),
                command.displayName(),
                command.email().orElse(null));
        ExternalIdentity identity = new ExternalIdentity(
                UUID.randomUUID(),
                scope.tenantId(),
                profile.getId(),
                command.issuer(),
                command.subject());
        TenantUserProfile created = store.create(scope, profile);
        ExternalIdentityReference linked = store.linkIdentity(scope, identity)
                .orElseThrow(() -> new IllegalStateException("New tenant user identity was not linked"));
        publish(scope, TenantAuditEvent.Action.USER_CREATED,
                TenantAuditEvent.TargetType.USER_PROFILE, created.id(), Optional.empty(), command.audit());
        publish(scope, TenantAuditEvent.Action.EXTERNAL_IDENTITY_LINKED,
                TenantAuditEvent.TargetType.EXTERNAL_IDENTITY, linked.id(), Optional.of(linked.issuer()), command.audit());
        return new ProvisionedTenantUser(created, linked);
    }

    public TenantUserProfile deactivate(UUID profileId, TenantUserCommands.Lifecycle command) {
        return changeActive(profileId, command, false);
    }

    public TenantUserProfile reactivate(UUID profileId, TenantUserCommands.Lifecycle command) {
        return changeActive(profileId, command, true);
    }

    public ExternalIdentityReference linkIdentity(
            UUID profileId,
            TenantUserCommands.LinkIdentity command) {
        ScopeContext scope = requireUserManagement();
        UUID requiredProfileId = requiredId(profileId, "profileId");
        Objects.requireNonNull(command, "command is required");
        identityProvider.requireConfiguredIssuer(command.issuer());
        ExternalIdentity identity = new ExternalIdentity(
                UUID.randomUUID(),
                scope.tenantId(),
                requiredProfileId,
                command.issuer(),
                command.subject());
        ExternalIdentityReference linked = store.linkIdentity(scope, identity)
                .orElseThrow(() -> new ResourceNotFoundException("Active tenant user"));
        publish(scope, TenantAuditEvent.Action.EXTERNAL_IDENTITY_LINKED,
                TenantAuditEvent.TargetType.EXTERNAL_IDENTITY, linked.id(), Optional.of(linked.issuer()), command.audit());
        return linked;
    }

    public long unlinkIdentity(
            UUID profileId,
            UUID identityId,
            TenantAuditMetadata audit) {
        ScopeContext scope = requireUserManagement();
        UUID requiredIdentityId = requiredId(identityId, "identityId");
        long version = store.unlinkIdentity(
                        scope,
                        requiredId(profileId, "profileId"),
                        requiredIdentityId)
                .orElseThrow(() -> new ResourceNotFoundException("Active external identity"));
        publish(scope, TenantAuditEvent.Action.EXTERNAL_IDENTITY_UNLINKED,
                TenantAuditEvent.TargetType.EXTERNAL_IDENTITY, requiredIdentityId, Optional.empty(), audit);
        return version;
    }

    private TenantUserProfile changeActive(
            UUID profileId,
            TenantUserCommands.Lifecycle command,
            boolean active) {
        ScopeContext scope = requireUserManagement();
        UUID requiredProfileId = requiredId(profileId, "profileId");
        Objects.requireNonNull(command, "command is required");
        if (!active) {
            administratorGuard.assertPathRemains(scope, requiredProfileId);
        }
        Optional<TenantUserProfile> updated = store.updateActive(
                scope,
                requiredProfileId,
                command.expectedVersion(),
                active);
        if (updated.isPresent()) {
            TenantUserProfile profile = updated.orElseThrow();
            publish(scope,
                    active ? TenantAuditEvent.Action.USER_REACTIVATED : TenantAuditEvent.Action.USER_DEACTIVATED,
                    TenantAuditEvent.TargetType.USER_PROFILE,
                    profile.id(),
                    Optional.empty(),
                    command.audit());
            return profile;
        }

        TenantUserProfile current = store.findById(scope, requiredProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant user"));
        if (current.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), current.version());
        }
        throw new ResourceStateConflictException(
                active ? "Tenant user is already active" : "Tenant user is already inactive");
    }

    private ScopeContext requireUserManagement() {
        return permissionEvaluator.requireTenant(Permission.IDENTITY_USER_MANAGE);
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            TenantAuditEvent.TargetType targetType,
            UUID targetId,
            Optional<String> targetReference,
            TenantAuditMetadata metadata) {
        Objects.requireNonNull(metadata, "audit is required");
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                targetType,
                Optional.of(targetId),
                targetReference,
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required");
    }
}
