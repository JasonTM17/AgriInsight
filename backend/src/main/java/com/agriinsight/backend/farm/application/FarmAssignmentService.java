package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.farm.domain.FarmAssignment;
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
public class FarmAssignmentService {

    private final PermissionEvaluator permissions;
    private final FarmAssignmentStore store;
    private final TenantAuditPublisher auditPublisher;

    public FarmAssignmentService(
            PermissionEvaluator permissions,
            FarmAssignmentStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public FarmAssignmentRecord grant(FarmAssignmentCommands.Grant command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        requireGrantTargets(scope, command);
        if (store.findActive(scope, command.userProfileId(), command.farmId()).isPresent()) {
            throw new ResourceStateConflictException("Farm assignment is already active");
        }
        FarmAssignmentRecord assignment = store.create(scope, new FarmAssignment(
                UUID.randomUUID(), scope.tenantId(), command.userProfileId(), command.farmId()));
        publish(scope, TenantAuditEvent.Action.FARM_ASSIGNMENT_GRANTED, assignment, command.audit());
        return assignment;
    }

    public FarmAssignmentRecord revoke(
            UUID assignmentId,
            FarmAssignmentCommands.Revoke command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        UUID requiredId = Objects.requireNonNull(assignmentId, "assignmentId is required");
        FarmAssignmentRecord current = requiredAssignment(scope, requiredId);
        requireVersion(command.expectedVersion(), current);
        if (!current.active()) {
            throw new ResourceStateConflictException("Farm assignment is already revoked");
        }

        FarmAssignmentRecord assignment = store.revoke(
                        scope, requiredId, command.expectedVersion())
                .orElseGet(() -> mutationFailure(scope, requiredId, command.expectedVersion()));
        publish(scope, TenantAuditEvent.Action.FARM_ASSIGNMENT_REVOKED, assignment, command.audit());
        return assignment;
    }

    ScopeContext requireManagement() {
        return permissions.requireTenant(Permission.FARM_ASSIGNMENT_MANAGE);
    }

    void requireGrantTargets(FarmAssignmentCommands.Grant command) {
        requireGrantTargets(requireManagement(), Objects.requireNonNull(command, "command is required"));
    }

    FarmAssignmentRecord getForManagement(UUID assignmentId) {
        ScopeContext scope = requireManagement();
        return requiredAssignment(scope, Objects.requireNonNull(assignmentId, "assignmentId is required"));
    }

    private void requireGrantTargets(
            ScopeContext scope,
            FarmAssignmentCommands.Grant command) {
        if (command.expectedVersion() != 0) {
            throw new ResourceStateConflictException("A new farm assignment must start at version 0");
        }
        if (!store.activeProfileExists(scope, command.userProfileId())) {
            throw new ResourceNotFoundException("Active tenant user");
        }
        if (!store.activeFarmExists(scope, command.farmId())) {
            throw new ResourceNotFoundException("Active farm");
        }
    }

    private FarmAssignmentRecord mutationFailure(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion) {
        FarmAssignmentRecord current = requiredAssignment(scope, assignmentId);
        requireVersion(expectedVersion, current);
        throw new ResourceStateConflictException("Farm assignment is already revoked");
    }

    private FarmAssignmentRecord requiredAssignment(
            ScopeContext scope,
            UUID assignmentId) {
        return store.findById(scope, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm assignment"));
    }

    private void requireVersion(long expectedVersion, FarmAssignmentRecord current) {
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            FarmAssignmentRecord assignment,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.FARM_ASSIGNMENT,
                Optional.of(assignment.id()),
                Optional.of(assignment.userProfileId() + ":" + assignment.farmId()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }
}
