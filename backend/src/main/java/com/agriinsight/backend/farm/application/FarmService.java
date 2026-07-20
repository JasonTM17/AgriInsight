package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.farm.domain.Farm;
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
public class FarmService {

    private final PermissionEvaluator permissions;
    private final FarmStore store;
    private final TenantAuditPublisher auditPublisher;

    public FarmService(
            PermissionEvaluator permissions,
            FarmStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public FarmPage list(FarmQuery query) {
        ScopeContext scope = permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public FarmRecord get(UUID farmId) {
        UUID requiredFarmId = requiredId(farmId);
        ScopeContext scope = permissions.requireDomain(Permission.FARM_READ, ScopeContext.Type.FARM, requiredFarmId);
        return requiredFarm(scope, requiredFarmId);
    }

    public FarmRecord create(FarmCommands.Create command) {
        ScopeContext scope = requireTenantManagement();
        Objects.requireNonNull(command, "command is required");
        Farm farm = new Farm(UUID.randomUUID(), scope.tenantId(), command.code(), command.displayName());
        FarmRecord created = store.create(scope, farm);
        publish(scope, TenantAuditEvent.Action.FARM_CREATED, created, command.audit());
        return created;
    }

    public FarmRecord update(UUID farmId, FarmCommands.Update command) {
        UUID requiredFarmId = requiredId(farmId);
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireFarmManagement(requiredFarmId);
        Optional<FarmRecord> updated = store.update(
                scope,
                requiredFarmId,
                command.expectedVersion(),
                command.code(),
                command.displayName());
        if (updated.isPresent()) {
            FarmRecord farm = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.FARM_UPDATED, farm, command.audit());
            return farm;
        }
        return failedMutation(scope, requiredFarmId, command.expectedVersion(), "Farm update does not change state");
    }

    public FarmRecord deactivate(UUID farmId, FarmCommands.Lifecycle command) {
        return changeActive(farmId, command, false);
    }

    public FarmRecord reactivate(UUID farmId, FarmCommands.Lifecycle command) {
        return changeActive(farmId, command, true);
    }

    ScopeContext requireTenantManagement() {
        return permissions.requireTenant(Permission.FARM_MANAGE);
    }

    ScopeContext requireFarmManagement(UUID farmId) {
        return permissions.requireDomain(Permission.FARM_MANAGE, ScopeContext.Type.FARM, requiredId(farmId));
    }

    FarmRecord getForTenantManagement(UUID farmId) {
        return requiredFarm(requireTenantManagement(), requiredId(farmId));
    }

    FarmRecord getForFarmManagement(UUID farmId) {
        UUID requiredFarmId = requiredId(farmId);
        return requiredFarm(requireFarmManagement(requiredFarmId), requiredFarmId);
    }

    private FarmRecord changeActive(
            UUID farmId,
            FarmCommands.Lifecycle command,
            boolean active) {
        UUID requiredFarmId = requiredId(farmId);
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        Optional<FarmRecord> updated = store.updateActive(
                scope, requiredFarmId, command.expectedVersion(), active);
        if (updated.isPresent()) {
            FarmRecord farm = updated.orElseThrow();
            publish(
                    scope,
                    active ? TenantAuditEvent.Action.FARM_REACTIVATED : TenantAuditEvent.Action.FARM_DEACTIVATED,
                    farm,
                    command.audit());
            return farm;
        }
        String message = active ? "Farm is already active" : "Farm is already inactive";
        return failedMutation(scope, requiredFarmId, command.expectedVersion(), message);
    }

    private FarmRecord failedMutation(
            ScopeContext scope,
            UUID farmId,
            long expectedVersion,
            String stateMessage) {
        FarmRecord current = requiredFarm(scope, farmId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        throw new ResourceStateConflictException(stateMessage);
    }

    private FarmRecord requiredFarm(ScopeContext scope, UUID farmId) {
        return store.findById(scope, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            FarmRecord farm,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.FARM,
                Optional.of(farm.id()),
                Optional.of(farm.code()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID farmId) {
        return Objects.requireNonNull(farmId, "farmId is required");
    }
}
