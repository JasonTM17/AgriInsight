package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.inventory.domain.Warehouse;
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
public class WarehouseService {

    private final PermissionEvaluator permissions;
    private final WarehouseStore store;
    private final TenantAuditPublisher auditPublisher;

    public WarehouseService(
            PermissionEvaluator permissions,
            WarehouseStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public WarehousePage list(WarehouseQuery query) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public WarehouseRecord get(UUID warehouseId) {
        UUID requiredId = requiredId(warehouseId);
        ScopeContext scope = permissions.requireDomain(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE, requiredId);
        return requiredWarehouse(scope, requiredId);
    }

    public WarehouseRecord create(WarehouseCommands.Create command) {
        ScopeContext scope = requireTenantManagement();
        Objects.requireNonNull(command, "command is required");
        var warehouse = new Warehouse(
                UUID.randomUUID(),
                scope.tenantId(),
                command.code(),
                command.displayName(),
                command.locationText());
        WarehouseRecord created = store.create(scope, warehouse);
        publish(scope, TenantAuditEvent.Action.WAREHOUSE_CREATED, created, command.audit());
        return created;
    }

    public WarehouseRecord update(UUID warehouseId, WarehouseCommands.Update command) {
        UUID requiredId = requiredId(warehouseId);
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireWarehouseManagement(requiredId);
        Optional<WarehouseRecord> updated = store.update(
                scope,
                requiredId,
                command.expectedVersion(),
                command.code(),
                command.displayName(),
                command.locationText());
        if (updated.isPresent()) {
            WarehouseRecord warehouse = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.WAREHOUSE_UPDATED, warehouse, command.audit());
            return warehouse;
        }
        return failedMutation(scope, requiredId, command.expectedVersion());
    }

    public WarehouseRecord deactivate(UUID warehouseId, WarehouseCommands.Lifecycle command) {
        return changeActive(warehouseId, command, false);
    }

    public WarehouseRecord reactivate(UUID warehouseId, WarehouseCommands.Lifecycle command) {
        return changeActive(warehouseId, command, true);
    }

    ScopeContext requireTenantManagement() {
        return permissions.requireTenant(Permission.INVENTORY_MANAGE);
    }

    ScopeContext requireWarehouseManagement(UUID warehouseId) {
        return permissions.requireDomain(
                Permission.INVENTORY_MANAGE,
                ScopeContext.Type.WAREHOUSE,
                requiredId(warehouseId));
    }

    WarehouseRecord getForTenantManagement(UUID warehouseId) {
        return requiredWarehouse(requireTenantManagement(), requiredId(warehouseId));
    }

    WarehouseRecord getForWarehouseManagement(UUID warehouseId) {
        UUID requiredId = requiredId(warehouseId);
        return requiredWarehouse(requireWarehouseManagement(requiredId), requiredId);
    }

    private WarehouseRecord changeActive(
            UUID warehouseId,
            WarehouseCommands.Lifecycle command,
            boolean active) {
        UUID requiredId = requiredId(warehouseId);
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        Optional<WarehouseRecord> updated = store.updateActive(
                scope, requiredId, command.expectedVersion(), active);
        if (updated.isPresent()) {
            WarehouseRecord warehouse = updated.orElseThrow();
            publish(
                    scope,
                    active
                            ? TenantAuditEvent.Action.WAREHOUSE_REACTIVATED
                            : TenantAuditEvent.Action.WAREHOUSE_DEACTIVATED,
                    warehouse,
                    command.audit());
            return warehouse;
        }
        return failedLifecycleMutation(scope, requiredId, command.expectedVersion(), active);
    }

    private WarehouseRecord failedLifecycleMutation(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion,
            boolean active) {
        WarehouseRecord current = requiredWarehouse(scope, warehouseId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        if (!active && current.active() && store.hasDeactivationBlockers(scope, warehouseId)) {
            throw new ResourceStateConflictException("Warehouse has active dependents or stock");
        }
        throw new ResourceStateConflictException(
                active ? "Warehouse is already active" : "Warehouse is already inactive");
    }

    private WarehouseRecord failedMutation(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion) {
        WarehouseRecord current = requiredWarehouse(scope, warehouseId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        throw new ResourceStateConflictException("Warehouse update does not change state");
    }

    private WarehouseRecord requiredWarehouse(ScopeContext scope, UUID warehouseId) {
        return store.findById(scope, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            WarehouseRecord warehouse,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.WAREHOUSE,
                Optional.of(warehouse.id()),
                Optional.of(warehouse.code()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID warehouseId) {
        return Objects.requireNonNull(warehouseId, "warehouseId is required");
    }
}
