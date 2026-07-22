package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class SeasonService {

    private final PermissionEvaluator permissions;
    private final SeasonStore store;
    private final TenantAuditPublisher auditPublisher;

    public SeasonService(
            PermissionEvaluator permissions,
            SeasonStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public SeasonPage list(SeasonQuery query) {
        ScopeContext scope = permissions.requireDomainList(Permission.SEASON_READ, ScopeContext.Type.FARM);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public SeasonRecord get(UUID seasonId) {
        ScopeContext scope = permissions.requireDomainList(Permission.SEASON_READ, ScopeContext.Type.FARM);
        return requiredSeason(scope, requiredId(seasonId));
    }

    public SeasonRecord create(SeasonCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireFarmManagement(command.farmId());
        requireLiveParents(scope, command.farmId(), command.fieldId(), command.cropId(), command.plantedAreaHectares());
        Season season = new Season(
                UUID.randomUUID(),
                scope.tenantId(),
                command.farmId(),
                command.fieldId(),
                command.cropId(),
                command.code(),
                command.displayName(),
                command.varietyName(),
                command.plannedStartDate(),
                command.plannedEndDate(),
                command.plantedAreaHectares(),
                command.budgetVnd());
        SeasonRecord created = store.create(scope, season)
                .orElseThrow(() -> createConflict(scope, command.farmId()));
        publish(scope, TenantAuditEvent.Action.SEASON_CREATED, created, command.audit());
        return created;
    }

    public SeasonRecord update(UUID seasonId, SeasonCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        SeasonRecord current = getForManagement(seasonId);
        ScopeContext scope = requireFarmManagement(current.farmId());
        SeasonTransitionPolicy.requireMutable(current);
        LocalDate start = command.plannedStartDate().orElse(current.plannedStartDate());
        LocalDate end = command.plannedEndDate().orElse(current.plannedEndDate());
        Season.requireDateRange(start, end);
        BigDecimal area = command.plantedAreaHectares().orElse(current.plantedAreaHectares());
        requireLiveParents(scope, current.farmId(), current.fieldId(), current.cropId(), area);
        Optional<SeasonRecord> updated = store.update(
                scope, current.id(), command.expectedVersion(), command);
        if (updated.isPresent()) {
            SeasonRecord season = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.SEASON_UPDATED, season, command.audit());
            return season;
        }
        return failedMutation(scope, current.id(), command.expectedVersion(), "Season update does not change state");
    }

    public SeasonRecord transition(UUID seasonId, SeasonCommands.Transition command) {
        Objects.requireNonNull(command, "command is required");
        SeasonRecord current = getForManagement(seasonId);
        ScopeContext scope = requireFarmManagement(current.farmId());
        SeasonTransitionPolicy.validate(current, command.targetStatus(), command.effectiveDate());
        if (command.targetStatus() == Season.Status.ACTIVE) {
            requireLiveParents(
                    scope,
                    current.farmId(),
                    current.fieldId(),
                    current.cropId(),
                    current.plantedAreaHectares());
        }
        Optional<SeasonRecord> updated = store.transition(
                scope,
                current.id(),
                command.expectedVersion(),
                current.status(),
                command.targetStatus(),
                command.effectiveDate());
        if (updated.isPresent()) {
            SeasonRecord season = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.SEASON_TRANSITIONED, season, command.audit());
            return season;
        }
        SeasonRecord latest = requiredSeason(scope, current.id());
        if (latest.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), latest.version());
        }
        throw new ResourceStateConflictException("Season transition is not allowed");
    }

    ScopeContext requireFarmManagement(UUID farmId) {
        UUID requiredFarmId = requiredId(farmId);
        ScopeContext scope = permissions.requireDomain(
                Permission.SEASON_MANAGE, ScopeContext.Type.FARM, requiredFarmId);
        if (!store.farmVisible(scope, requiredFarmId)) {
            throw new ResourceNotFoundException("Farm");
        }
        return scope;
    }

    SeasonRecord getForManagement(UUID seasonId) {
        ScopeContext scope = permissions.requireDomainList(Permission.SEASON_MANAGE, ScopeContext.Type.FARM);
        SeasonRecord season = requiredSeason(scope, requiredId(seasonId));
        requireFarmManagement(season.farmId());
        return season;
    }

    private SeasonRecord failedMutation(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            String stateMessage) {
        SeasonRecord current = requiredSeason(scope, seasonId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        SeasonTransitionPolicy.requireMutable(current);
        throw new ResourceStateConflictException(stateMessage);
    }

    private void requireLiveParents(
            ScopeContext scope,
            UUID farmId,
            UUID fieldId,
            UUID cropId,
            BigDecimal area) {
        if (!store.liveParentsAvailable(scope, farmId, fieldId, cropId, area)) {
            throw new ResourceStateConflictException("Season requires active parents and available field area");
        }
    }

    private RuntimeException createConflict(ScopeContext scope, UUID farmId) {
        if (!store.farmVisible(scope, farmId)) {
            return new ResourceNotFoundException("Farm");
        }
        return new ResourceStateConflictException(
                "Season requires active parents and available field area");
    }

    private SeasonRecord requiredSeason(ScopeContext scope, UUID seasonId) {
        return store.findById(scope, seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            SeasonRecord season,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.SEASON,
                Optional.of(season.id()),
                Optional.of(season.code()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "id is required");
    }
}
