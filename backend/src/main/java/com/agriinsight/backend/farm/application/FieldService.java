package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.farm.domain.Field;
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
public class FieldService {

    private final PermissionEvaluator permissions;
    private final FieldStore store;
    private final TenantAuditPublisher auditPublisher;

    public FieldService(
            PermissionEvaluator permissions,
            FieldStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public FieldPage list(FieldQuery query) {
        ScopeContext scope = permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public FieldRecord get(UUID fieldId) {
        ScopeContext scope = permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM);
        return requiredField(scope, requiredId(fieldId));
    }

    public FieldRecord create(FieldCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireFarmManagement(command.farmId());
        requireAvailableParents(scope, command.farmId(), command.responsibleEmployeeId());
        Field field = new Field(
                UUID.randomUUID(), scope.tenantId(), command.farmId(), command.code(),
                command.displayName(), command.areaHectares(), command.responsibleEmployeeId(),
                command.coordinates(), command.soilType(), command.irrigationType());
        FieldRecord created = store.create(scope, field);
        publish(scope, TenantAuditEvent.Action.FIELD_CREATED, created, command.audit());
        return created;
    }

    public FieldRecord update(UUID fieldId, FieldCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        FieldRecord current = getForManagement(fieldId);
        ScopeContext scope = requireFarmManagement(current.farmId());
        Optional<UUID> responsible = command.responsibleEmployeeId()
                .orElse(current.responsibleEmployeeId());
        if (current.active() || command.responsibleEmployeeId().filter(Optional::isPresent).isPresent()) {
            requireAvailableParents(scope, current.farmId(), responsible);
        }
        Optional<FieldRecord> updated = store.update(
                scope, current.id(), command.expectedVersion(), command);
        if (updated.isPresent()) {
            FieldRecord field = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.FIELD_UPDATED, field, command.audit());
            return field;
        }
        return failedMutation(scope, current.id(), command.expectedVersion(), "Field update does not change state");
    }

    public FieldRecord deactivate(UUID fieldId, FieldCommands.Lifecycle command) {
        return changeActive(fieldId, command, false);
    }

    public FieldRecord reactivate(UUID fieldId, FieldCommands.Lifecycle command) {
        return changeActive(fieldId, command, true);
    }

    ScopeContext requireFarmManagement(UUID farmId) {
        UUID requiredFarmId = requiredId(farmId);
        ScopeContext scope = permissions.requireDomain(
                Permission.FARM_MANAGE, ScopeContext.Type.FARM, requiredFarmId);
        if (!store.farmVisible(scope, requiredFarmId)) {
            throw new ResourceNotFoundException("Farm");
        }
        return scope;
    }

    FieldRecord getForManagement(UUID fieldId) {
        ScopeContext scope = permissions.requireDomainList(Permission.FARM_MANAGE, ScopeContext.Type.FARM);
        FieldRecord field = requiredField(scope, requiredId(fieldId));
        requireFarmManagement(field.farmId());
        return field;
    }

    private FieldRecord changeActive(
            UUID fieldId,
            FieldCommands.Lifecycle command,
            boolean active) {
        Objects.requireNonNull(command, "command is required");
        FieldRecord current = getForManagement(fieldId);
        ScopeContext scope = requireFarmManagement(current.farmId());
        if (active) {
            requireAvailableParents(scope, current.farmId(), current.responsibleEmployeeId());
        }
        Optional<FieldRecord> updated = store.updateActive(
                scope, current.id(), command.expectedVersion(), active);
        if (updated.isPresent()) {
            FieldRecord field = updated.orElseThrow();
            publish(
                    scope,
                    active ? TenantAuditEvent.Action.FIELD_REACTIVATED
                            : TenantAuditEvent.Action.FIELD_DEACTIVATED,
                    field,
                    command.audit());
            return field;
        }
        return failedLifecycleMutation(scope, current.id(), command.expectedVersion(), active);
    }

    private FieldRecord failedLifecycleMutation(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            boolean active) {
        FieldRecord current = requiredField(scope, fieldId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        if (!active && current.active() && store.hasDeactivationBlockers(scope, fieldId)) {
            throw new ResourceStateConflictException("Field has live seasons or activities");
        }
        if (active && !current.active()) {
            requireAvailableParents(scope, current.farmId(), current.responsibleEmployeeId());
        }
        throw new ResourceStateConflictException(active ? "Field is already active" : "Field is already inactive");
    }

    private FieldRecord failedMutation(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            String stateMessage) {
        FieldRecord current = requiredField(scope, fieldId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        throw new ResourceStateConflictException(stateMessage);
    }

    private void requireAvailableParents(
            ScopeContext scope,
            UUID farmId,
            Optional<UUID> responsibleEmployeeId) {
        if (!store.liveParentsAvailable(scope, farmId, responsibleEmployeeId)) {
            throw new ResourceStateConflictException("Field requires an active farm and responsible employee");
        }
    }

    private FieldRecord requiredField(ScopeContext scope, UUID fieldId) {
        return store.findById(scope, fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            FieldRecord field,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope, action, TenantAuditEvent.TargetType.FIELD,
                Optional.of(field.id()), Optional.of(field.code()),
                metadata.reasonCode(), metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "id is required");
    }
}
