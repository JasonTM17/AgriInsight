package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.farm.domain.Crop;
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
public class CropService {

    private final PermissionEvaluator permissions;
    private final CropStore store;
    private final TenantAuditPublisher auditPublisher;

    public CropService(
            PermissionEvaluator permissions,
            CropStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public CropPage list(CropQuery query) {
        ScopeContext scope = permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public CropRecord get(UUID cropId) {
        ScopeContext scope = permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM);
        return requiredCrop(scope, requiredId(cropId));
    }

    public CropRecord create(CropCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        Crop crop = new Crop(
                UUID.randomUUID(), scope.tenantId(), command.code(),
                command.displayName(), command.scientificName());
        CropRecord created = store.create(scope, crop);
        publish(scope, TenantAuditEvent.Action.CROP_CREATED, created, command.audit());
        return created;
    }

    public CropRecord update(UUID cropId, CropCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        UUID requiredCropId = requiredId(cropId);
        requiredCrop(scope, requiredCropId);
        Optional<CropRecord> updated = store.update(
                scope, requiredCropId, command.expectedVersion(), command);
        if (updated.isPresent()) {
            CropRecord crop = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.CROP_UPDATED, crop, command.audit());
            return crop;
        }
        return failedMutation(scope, requiredCropId, command.expectedVersion(), "Crop update does not change state");
    }

    public CropRecord deactivate(UUID cropId, CropCommands.Lifecycle command) {
        return changeActive(cropId, command, false);
    }

    public CropRecord reactivate(UUID cropId, CropCommands.Lifecycle command) {
        return changeActive(cropId, command, true);
    }

    ScopeContext requireTenantManagement() {
        return permissions.requireTenant(Permission.FARM_MANAGE);
    }

    CropRecord getForTenantManagement(UUID cropId) {
        return requiredCrop(requireTenantManagement(), requiredId(cropId));
    }

    private CropRecord changeActive(
            UUID cropId,
            CropCommands.Lifecycle command,
            boolean active) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        UUID requiredCropId = requiredId(cropId);
        requiredCrop(scope, requiredCropId);
        Optional<CropRecord> updated = store.updateActive(
                scope, requiredCropId, command.expectedVersion(), active);
        if (updated.isPresent()) {
            CropRecord crop = updated.orElseThrow();
            publish(
                    scope,
                    active ? TenantAuditEvent.Action.CROP_REACTIVATED
                            : TenantAuditEvent.Action.CROP_DEACTIVATED,
                    crop,
                    command.audit());
            return crop;
        }
        return failedLifecycleMutation(scope, requiredCropId, command.expectedVersion(), active);
    }

    private CropRecord failedLifecycleMutation(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            boolean active) {
        CropRecord current = requiredCrop(scope, cropId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        if (!active && current.active() && store.hasDeactivationBlockers(scope, cropId)) {
            throw new ResourceStateConflictException("Crop has live seasons");
        }
        throw new ResourceStateConflictException(active ? "Crop is already active" : "Crop is already inactive");
    }

    private CropRecord failedMutation(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            String stateMessage) {
        CropRecord current = requiredCrop(scope, cropId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        throw new ResourceStateConflictException(stateMessage);
    }

    private CropRecord requiredCrop(ScopeContext scope, UUID cropId) {
        return store.findById(scope, cropId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            CropRecord crop,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope, action, TenantAuditEvent.TargetType.CROP,
                Optional.of(crop.id()), Optional.of(crop.code()),
                metadata.reasonCode(), metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "id is required");
    }
}
