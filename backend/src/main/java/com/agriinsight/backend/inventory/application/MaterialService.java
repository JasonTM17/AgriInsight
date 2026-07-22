package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.inventory.domain.Material;
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
public class MaterialService {

    private final PermissionEvaluator permissions;
    private final MaterialStore store;
    private final TenantAuditPublisher auditPublisher;

    public MaterialService(
            PermissionEvaluator permissions,
            MaterialStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public MaterialPage list(MaterialQuery query) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public MaterialRecord get(UUID materialId) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE);
        return requiredMaterial(scope, requiredId(materialId));
    }

    public MaterialRecord create(MaterialCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        Material material = new Material(
                UUID.randomUUID(),
                scope.tenantId(),
                command.code(),
                command.displayName(),
                command.baseUnit(),
                command.minimumStockQuantity());
        MaterialRecord created = store.create(scope, material);
        publish(scope, TenantAuditEvent.Action.MATERIAL_CREATED, created, command.audit());
        return created;
    }

    public MaterialRecord update(UUID materialId, MaterialCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        UUID target = requiredId(materialId);
        requiredMaterial(scope, target);
        Optional<MaterialRecord> updated = store.update(
                scope, target, command.expectedVersion(), command);
        if (updated.isPresent()) {
            MaterialRecord material = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.MATERIAL_UPDATED, material, command.audit());
            return material;
        }
        return failedMutation(scope, target, command);
    }

    public MaterialRecord deactivate(UUID materialId, MaterialCommands.Lifecycle command) {
        return changeActive(materialId, command, false);
    }

    public MaterialRecord reactivate(UUID materialId, MaterialCommands.Lifecycle command) {
        return changeActive(materialId, command, true);
    }

    ScopeContext requireTenantManagement() {
        return permissions.requireTenant(Permission.INVENTORY_MANAGE);
    }

    MaterialRecord getForTenantManagement(UUID materialId) {
        return requiredMaterial(requireTenantManagement(), requiredId(materialId));
    }

    private MaterialRecord changeActive(
            UUID materialId,
            MaterialCommands.Lifecycle command,
            boolean active) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        UUID target = requiredId(materialId);
        requiredMaterial(scope, target);
        Optional<MaterialRecord> updated = store.updateActive(
                scope, target, command.expectedVersion(), active);
        if (updated.isPresent()) {
            MaterialRecord material = updated.orElseThrow();
            publish(
                    scope,
                    active ? TenantAuditEvent.Action.MATERIAL_REACTIVATED
                            : TenantAuditEvent.Action.MATERIAL_DEACTIVATED,
                    material,
                    command.audit());
            return material;
        }
        return failedLifecycleMutation(scope, target, command.expectedVersion(), active);
    }

    private MaterialRecord failedLifecycleMutation(
            ScopeContext scope,
            UUID materialId,
            long expectedVersion,
            boolean active) {
        MaterialRecord current = requiredMaterial(scope, materialId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        if (!active && current.active() && store.hasReferences(scope, materialId)) {
            throw new ResourceStateConflictException("Material has inventory references");
        }
        throw new ResourceStateConflictException(
                active ? "Material is already active" : "Material is already inactive");
    }

    private MaterialRecord failedMutation(
            ScopeContext scope,
            UUID materialId,
            MaterialCommands.Update command) {
        MaterialRecord current = requiredMaterial(scope, materialId);
        if (current.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), current.version());
        }
        if (command.baseUnit().filter(unit -> unit != current.baseUnit()).isPresent()
                && store.hasReferences(scope, materialId)) {
            throw new ResourceStateConflictException(
                    "Material base unit is immutable after inventory use");
        }
        throw new ResourceStateConflictException("Material update does not change state");
    }

    private MaterialRecord requiredMaterial(ScopeContext scope, UUID materialId) {
        return store.findById(scope, materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            MaterialRecord material,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.MATERIAL,
                Optional.of(material.id()),
                Optional.of(material.code()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID materialId) {
        return Objects.requireNonNull(materialId, "materialId is required");
    }
}
