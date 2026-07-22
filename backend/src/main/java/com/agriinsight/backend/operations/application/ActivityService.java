package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.time.Instant;
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
public class ActivityService {

    private final PermissionEvaluator permissions;
    private final ActivityStore store;
    private final TenantAuditPublisher auditPublisher;

    public ActivityService(
            PermissionEvaluator permissions,
            ActivityStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public ActivityPage list(ActivityQuery query) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.ACTIVITY_READ, ScopeContext.Type.ACTIVITY);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public ActivityRecord get(UUID activityId) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.ACTIVITY_READ, ScopeContext.Type.ACTIVITY);
        return requiredActivity(scope, requiredId(activityId));
    }

    public ActivityRecord create(ActivityCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireFarmManagement(command.farmId());
        requireLiveParents(
                scope, command.farmId(), command.fieldId(), command.seasonId(), command.activityType());
        Activity activity = new Activity(
                UUID.randomUUID(), scope.tenantId(), command.farmId(), command.fieldId(),
                command.seasonId(), command.activityType(), command.code(), command.title(),
                command.description(), command.plannedStartAt(), command.dueAt());
        ActivityRecord created = store.create(scope, activity)
                .orElseThrow(() -> createConflict(scope, command.farmId()));
        publish(scope, TenantAuditEvent.Action.ACTIVITY_CREATED, created, command.audit());
        return created;
    }

    public ActivityRecord update(UUID activityId, ActivityCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        ActivityRecord current = getForManagement(activityId);
        ScopeContext scope = requireFarmManagement(current.farmId());
        ActivityTransitionPolicy.requireMutable(current);
        Instant plannedStart = command.plannedStartAt().orElse(current.plannedStartAt());
        Instant due = command.dueAt().orElse(current.dueAt());
        Activity.requireSchedule(plannedStart, due);
        ActivityType activityType = command.activityType().orElse(current.activityType());
        requireLiveParents(scope, current.farmId(), current.fieldId(), current.seasonId(), activityType);
        Optional<ActivityRecord> updated = store.update(
                scope, current.id(), command.expectedVersion(), command);
        if (updated.isPresent()) {
            ActivityRecord activity = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.ACTIVITY_UPDATED, activity, command.audit());
            return activity;
        }
        return failedMutation(scope, current.id(), command.expectedVersion());
    }

    public ActivityRecord transition(UUID activityId, ActivityCommands.Transition command) {
        Objects.requireNonNull(command, "command is required");
        ActivityRecord current = getForManagement(activityId);
        ScopeContext scope = requireFarmManagement(current.farmId());
        ActivityTransitionPolicy.validate(current, command.targetStatus(), command.effectiveAt());
        if (command.targetStatus() == ActivityStatus.STARTED) {
            requireLiveParents(
                    scope, current.farmId(), current.fieldId(), current.seasonId(), current.activityType());
        }
        Optional<ActivityRecord> updated = store.transition(
                scope,
                current.id(),
                command.expectedVersion(),
                current.status(),
                command.targetStatus(),
                command.effectiveAt());
        if (updated.isPresent()) {
            ActivityRecord activity = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.ACTIVITY_TRANSITIONED, activity, command.audit());
            return activity;
        }
        ActivityRecord latest = requiredActivity(scope, current.id());
        if (latest.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), latest.version());
        }
        throw new ResourceStateConflictException("Activity transition is not allowed");
    }

    ScopeContext requireFarmManagement(UUID farmId) {
        UUID requiredFarmId = requiredId(farmId);
        ScopeContext scope = permissions.requireDomain(
                Permission.ACTIVITY_MANAGE, ScopeContext.Type.FARM, requiredFarmId);
        if (!store.farmVisible(scope, requiredFarmId)) {
            throw new ResourceNotFoundException("Farm");
        }
        return scope;
    }

    ScopeContext requireManagementScope() {
        return permissions.requireDomainList(
                Permission.ACTIVITY_MANAGE, ScopeContext.Type.ACTIVITY);
    }

    ActivityRecord getForManagement(UUID activityId) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.ACTIVITY_MANAGE, ScopeContext.Type.ACTIVITY);
        ActivityRecord activity = requiredActivity(scope, requiredId(activityId));
        requireFarmManagement(activity.farmId());
        return activity;
    }

    private ActivityRecord failedMutation(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion) {
        ActivityRecord current = requiredActivity(scope, activityId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        ActivityTransitionPolicy.requireMutable(current);
        throw new ResourceStateConflictException("Activity update does not change state");
    }

    private void requireLiveParents(
            ScopeContext scope,
            UUID farmId,
            UUID fieldId,
            UUID seasonId,
            ActivityType activityType) {
        if (!store.liveParentsAvailable(scope, farmId, fieldId, seasonId, activityType)) {
            throw new ResourceStateConflictException(
                    "Activity requires active farm, field, season and activity type");
        }
    }

    private RuntimeException createConflict(ScopeContext scope, UUID farmId) {
        if (!store.farmVisible(scope, farmId)) {
            return new ResourceNotFoundException("Farm");
        }
        return new ResourceStateConflictException(
                "Activity requires active farm, field, season and activity type");
    }

    private ActivityRecord requiredActivity(ScopeContext scope, UUID activityId) {
        return store.findById(scope, activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            ActivityRecord activity,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.ACTIVITY,
                Optional.of(activity.id()),
                Optional.of(activity.code()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "activityId is required");
    }
}
