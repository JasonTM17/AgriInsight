package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.inventory.domain.WarehouseAssignment;
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
public class WarehouseAssignmentService {

    private final PermissionEvaluator permissions;
    private final WarehouseAssignmentStore store;
    private final TenantAuditPublisher auditPublisher;

    public WarehouseAssignmentService(
            PermissionEvaluator permissions,
            WarehouseAssignmentStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public WarehouseAssignmentRecord grant(WarehouseAssignmentCommands.Grant command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        requireGrantTargets(scope, command);
        if (store.findActive(scope, command.userProfileId(), command.warehouseId()).isPresent()) {
            throw new ResourceStateConflictException("Warehouse assignment is already active");
        }
        WarehouseAssignmentRecord assignment = store.create(scope, new WarehouseAssignment(
                        UUID.randomUUID(), scope.tenantId(),
                        command.userProfileId(), command.warehouseId()))
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Warehouse assignment is already active"));
        publish(
                scope,
                TenantAuditEvent.Action.WAREHOUSE_ASSIGNMENT_GRANTED,
                assignment,
                command.audit());
        return assignment;
    }

    public WarehouseAssignmentPage list(WarehouseAssignmentQuery query) {
        return store.findAll(
                requireManagement(), Objects.requireNonNull(query, "query is required"));
    }

    public WarehouseAssignmentRecord revoke(
            UUID assignmentId,
            WarehouseAssignmentCommands.Revoke command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireManagement();
        UUID target = Objects.requireNonNull(assignmentId, "assignmentId is required");
        WarehouseAssignmentRecord current = requiredAssignment(scope, target);
        requireVersion(command.expectedVersion(), current);
        if (!current.active()) {
            throw new ResourceStateConflictException("Warehouse assignment is already revoked");
        }
        WarehouseAssignmentRecord assignment = store.revoke(
                        scope, target, command.expectedVersion())
                .orElseGet(() -> mutationFailure(scope, target, command.expectedVersion()));
        publish(
                scope,
                TenantAuditEvent.Action.WAREHOUSE_ASSIGNMENT_REVOKED,
                assignment,
                command.audit());
        return assignment;
    }

    ScopeContext requireManagement() {
        return permissions.requireTenant(Permission.INVENTORY_ASSIGNMENT_MANAGE);
    }

    void requireGrantTargets(WarehouseAssignmentCommands.Grant command) {
        requireGrantTargets(
                requireManagement(), Objects.requireNonNull(command, "command is required"));
    }

    WarehouseAssignmentRecord getForManagement(UUID assignmentId) {
        ScopeContext scope = requireManagement();
        return requiredAssignment(
                scope, Objects.requireNonNull(assignmentId, "assignmentId is required"));
    }

    private void requireGrantTargets(
            ScopeContext scope,
            WarehouseAssignmentCommands.Grant command) {
        if (command.expectedVersion() != 0) {
            throw new ResourceStateConflictException(
                    "A new warehouse assignment must start at version 0");
        }
        if (!store.activeProfileExists(scope, command.userProfileId())) {
            throw new ResourceNotFoundException("Active tenant user");
        }
        if (!store.activeWarehouseExists(scope, command.warehouseId())) {
            throw new ResourceNotFoundException("Active warehouse");
        }
    }

    private WarehouseAssignmentRecord mutationFailure(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion) {
        WarehouseAssignmentRecord current = requiredAssignment(scope, assignmentId);
        requireVersion(expectedVersion, current);
        throw new ResourceStateConflictException("Warehouse assignment is already revoked");
    }

    private WarehouseAssignmentRecord requiredAssignment(
            ScopeContext scope,
            UUID assignmentId) {
        return store.findById(scope, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse assignment"));
    }

    private void requireVersion(long expectedVersion, WarehouseAssignmentRecord current) {
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            WarehouseAssignmentRecord assignment,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.WAREHOUSE_ASSIGNMENT,
                Optional.of(assignment.id()),
                Optional.of(assignment.userProfileId() + ":" + assignment.warehouseId()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }
}
