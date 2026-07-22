package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
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
public class HarvestService {

    private final PermissionEvaluator permissions;
    private final HarvestStore store;
    private final TenantAuditPublisher auditPublisher;

    public HarvestService(
            PermissionEvaluator permissions,
            HarvestStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public HarvestPage list(HarvestQuery query) {
        ScopeContext scope = permissions.requireDomainList(Permission.HARVEST_READ, ScopeContext.Type.FARM);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public HarvestRecord get(UUID harvestId) {
        ScopeContext scope = permissions.requireDomainList(Permission.HARVEST_READ, ScopeContext.Type.FARM);
        return required(scope, harvestId);
    }

    public HarvestRecord post(HarvestCommands.Post command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requirePostTarget(command);
        HarvestRecord created = store.append(scope, new Harvest(
                UUID.randomUUID(), scope.tenantId(), command.farmId(), command.fieldId(),
                command.seasonId(), command.cropId(), scope.profileId(), command.occurredOn(),
                command.quantityKg(), command.wasteQuantityKg(), command.qualityGrade(),
                command.revenueVnd(), Optional.empty(), Optional.empty(), Optional.empty()))
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Harvest requires an available season hierarchy"));
        publish(scope, TenantAuditEvent.Action.HARVEST_POSTED, created, command.audit());
        return created;
    }

    public HarvestRecord correct(UUID harvestId, HarvestCommands.Correct command) {
        Objects.requireNonNull(command, "command is required");
        HarvestRecord original = getForManagement(harvestId);
        ScopeContext scope = requireFarmManagement(original.farmId());
        HarvestRecord correction = store.append(scope, new Harvest(
                UUID.randomUUID(), scope.tenantId(), original.farmId(), original.fieldId(),
                original.seasonId(), original.cropId(), scope.profileId(), command.occurredOn(),
                command.quantityKg(), command.wasteQuantityKg(), command.qualityGrade(),
                command.revenueVnd(), Optional.of(original.id()),
                Optional.of(command.correctionKind()), Optional.of(command.correctionReason())))
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Harvest correction target is unavailable or already corrected"));
        publish(scope, TenantAuditEvent.Action.HARVEST_CORRECTED, correction, command.audit());
        return correction;
    }

    ScopeContext requirePostTarget(HarvestCommands.Post command) {
        ScopeContext scope = requireFarmManagement(command.farmId());
        if (!store.postTargetAvailable(
                scope, command.farmId(), command.fieldId(), command.seasonId(),
                command.cropId(), command.occurredOn())) {
            throw new ResourceStateConflictException("Harvest requires an available season hierarchy");
        }
        return scope;
    }

    HarvestRecord getForManagement(UUID harvestId) {
        ScopeContext scope = permissions.requireDomainList(Permission.HARVEST_MANAGE, ScopeContext.Type.FARM);
        HarvestRecord harvest = required(scope, harvestId);
        requireFarmManagement(harvest.farmId());
        return harvest;
    }

    HarvestRecord getForReplay(UUID harvestId) {
        return getForManagement(harvestId);
    }

    private ScopeContext requireFarmManagement(UUID farmId) {
        UUID requiredFarmId = requiredId(farmId);
        ScopeContext scope = permissions.requireDomain(
                Permission.HARVEST_MANAGE, ScopeContext.Type.FARM, requiredFarmId);
        if (!store.farmVisible(scope, requiredFarmId)) {
            throw new ResourceNotFoundException("Farm");
        }
        return scope;
    }

    private HarvestRecord required(ScopeContext scope, UUID harvestId) {
        return store.findById(scope, requiredId(harvestId))
                .orElseThrow(() -> new ResourceNotFoundException("Harvest"));
    }

    private void publish(
            ScopeContext scope, TenantAuditEvent.Action action,
            HarvestRecord harvest, TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope, action, TenantAuditEvent.TargetType.HARVEST,
                Optional.of(harvest.id()), Optional.of(harvest.seasonId() + ":" + harvest.occurredOn()),
                metadata.reasonCode(), metadata.correlationId(), TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "id is required");
    }
}
