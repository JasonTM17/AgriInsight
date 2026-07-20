package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
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
public class TenantRoleAssignmentService {

    private final PermissionEvaluator permissionEvaluator;
    private final TenantRoleAssignmentStore store;
    private final TenantAdministratorGuard administratorGuard;
    private final TenantAuditPublisher auditPublisher;

    public TenantRoleAssignmentService(
            PermissionEvaluator permissionEvaluator,
            TenantRoleAssignmentStore store,
            TenantAdministratorGuard administratorGuard,
            TenantAuditPublisher auditPublisher) {
        this.permissionEvaluator = Objects.requireNonNull(permissionEvaluator, "permissionEvaluator is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.administratorGuard = Objects.requireNonNull(administratorGuard, "administratorGuard is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public TenantRoleAssignment grant(
            UUID profileId,
            TenantRoleAssignmentCommands.Grant command) {
        ScopeContext scope = requireRoleManagement();
        Objects.requireNonNull(command, "command is required");
        UUID requiredProfileId = requireProfile(scope, profileId);

        Optional<TenantRoleAssignment> current = store.find(scope, requiredProfileId, command.role());
        Optional<TenantRoleAssignment> updated;
        if (current.isEmpty()) {
            if (command.expectedVersion() != 0) {
                throw new ResourceStateConflictException("A new role assignment must start at version 0");
            }
            updated = store.create(
                    scope,
                    UUID.randomUUID(),
                    requiredProfileId,
                    command.role());
        } else {
            TenantRoleAssignment assignment = current.orElseThrow();
            requireVersion(command.expectedVersion(), assignment);
            if (assignment.active()) {
                throw new ResourceStateConflictException("Role assignment is already active");
            }
            updated = store.reactivate(
                    scope,
                    requiredProfileId,
                    command.role(),
                    command.expectedVersion());
        }

        TenantRoleAssignment assignment = updated.orElseGet(() -> mutationFailure(
                scope,
                requiredProfileId,
                command.role(),
                command.expectedVersion(),
                true));
        publish(scope, TenantAuditEvent.Action.ROLE_GRANTED, assignment, command.audit());
        return assignment;
    }

    public TenantRoleAssignment revoke(
            UUID profileId,
            Role role,
            TenantRoleAssignmentCommands.Revoke command) {
        ScopeContext scope = requireRoleManagement();
        Role requiredRole = Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(command, "command is required");
        UUID requiredProfileId = requireProfile(scope, profileId);

        TenantRoleAssignment current = store.find(scope, requiredProfileId, requiredRole)
                .orElseThrow(() -> new ResourceNotFoundException("Role assignment"));
        requireVersion(command.expectedVersion(), current);
        if (!current.active()) {
            throw new ResourceStateConflictException("Role assignment is already revoked");
        }
        if (requiredRole == Role.TENANT_ADMIN) {
            administratorGuard.assertPathRemains(scope, requiredProfileId);
        }

        TenantRoleAssignment assignment = store.revoke(
                        scope,
                        requiredProfileId,
                        requiredRole,
                        command.expectedVersion())
                .orElseGet(() -> mutationFailure(
                        scope,
                        requiredProfileId,
                        requiredRole,
                        command.expectedVersion(),
                        false));
        publish(scope, TenantAuditEvent.Action.ROLE_REVOKED, assignment, command.audit());
        return assignment;
    }

    public TenantRoleAssignment get(UUID profileId, Role role) {
        ScopeContext scope = requireRoleManagement();
        UUID requiredProfileId = requireProfile(scope, profileId);
        return store.find(scope, requiredProfileId, Objects.requireNonNull(role, "role is required"))
                .orElseThrow(() -> new ResourceNotFoundException("Role assignment"));
    }

    ScopeContext requireRoleManagement() {
        return permissionEvaluator.requireTenant(Permission.IDENTITY_ROLE_MANAGE);
    }

    private UUID requireProfile(ScopeContext scope, UUID profileId) {
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        if (!store.profileExists(scope, requiredProfileId)) {
            throw new ResourceNotFoundException("Tenant user");
        }
        return requiredProfileId;
    }

    private TenantRoleAssignment mutationFailure(
            ScopeContext scope,
            UUID profileId,
            Role role,
            long expectedVersion,
            boolean requestedActive) {
        TenantRoleAssignment current = store.find(scope, profileId, role)
                .orElseThrow(() -> new IllegalStateException("Role assignment mutation returned no row"));
        requireVersion(expectedVersion, current);
        throw new ResourceStateConflictException(
                requestedActive ? "Role assignment is already active" : "Role assignment is already revoked");
    }

    private void requireVersion(long expectedVersion, TenantRoleAssignment current) {
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            TenantRoleAssignment assignment,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.USER_ROLE,
                Optional.of(assignment.id()),
                Optional.of(assignment.role().name()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }
}
