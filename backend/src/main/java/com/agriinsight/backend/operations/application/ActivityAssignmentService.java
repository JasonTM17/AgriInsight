package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.operations.domain.ActivityAssignment;
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
public class ActivityAssignmentService {

    private final ActivityService activities;
    private final ActivityAssignmentStore store;
    private final TenantAuditPublisher auditPublisher;

    public ActivityAssignmentService(
            ActivityService activities,
            ActivityAssignmentStore store,
            TenantAuditPublisher auditPublisher) {
        this.activities = Objects.requireNonNull(activities, "activities is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public ActivityAssignmentRecord grant(
            UUID activityId,
            ActivityAssignmentCommands.Grant command) {
        Objects.requireNonNull(command, "command is required");
        ActivityRecord activity = activities.getForManagement(requiredId(activityId));
        ScopeContext scope = activities.requireFarmManagement(activity.farmId());
        requireGrantTarget(scope, activity, command);
        if (store.findActive(scope, activity.id(), command.employeeId()).isPresent()) {
            throw new ResourceStateConflictException("Activity assignment is already active");
        }
        ActivityAssignmentRecord assignment = store.create(scope, new ActivityAssignment(
                UUID.randomUUID(), scope.tenantId(), activity.id(), command.employeeId()))
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Activity assignment requires a live activity and active employee"));
        publish(scope, TenantAuditEvent.Action.ACTIVITY_ASSIGNMENT_GRANTED, assignment, command.audit());
        return assignment;
    }

    public ActivityAssignmentRecord revoke(
            UUID assignmentId,
            ActivityAssignmentCommands.Revoke command) {
        Objects.requireNonNull(command, "command is required");
        ActivityAssignmentRecord current = getForManagement(assignmentId);
        ScopeContext scope = activityScope(current.activityId());
        requireVersion(command.expectedVersion(), current);
        if (!current.active()) {
            throw new ResourceStateConflictException("Activity assignment is already revoked");
        }
        ActivityAssignmentRecord assignment = store.revoke(
                scope, current.id(), command.expectedVersion())
                .orElseGet(() -> mutationFailure(scope, current.id(), command.expectedVersion()));
        publish(scope, TenantAuditEvent.Action.ACTIVITY_ASSIGNMENT_REVOKED, assignment, command.audit());
        return assignment;
    }

    void requireGrantTarget(
            UUID activityId,
            ActivityAssignmentCommands.Grant command) {
        ActivityRecord activity = activities.getForManagement(requiredId(activityId));
        requireGrantTarget(activities.requireFarmManagement(activity.farmId()), activity, command);
    }

    ActivityAssignmentRecord getForManagement(UUID assignmentId) {
        ScopeContext lookupScope = activities.requireManagementScope();
        ActivityAssignmentRecord assignment = store.findById(
                lookupScope, requiredId(assignmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Activity assignment"));
        activities.getForManagement(assignment.activityId());
        return assignment;
    }

    private void requireGrantTarget(
            ScopeContext scope,
            ActivityRecord activity,
            ActivityAssignmentCommands.Grant command) {
        if (command.expectedVersion() != 0) {
            throw new ResourceStateConflictException(
                    "A new activity assignment must start at version 0");
        }
        if (!store.activeEmployeeExists(scope, command.employeeId())) {
            throw new ResourceNotFoundException("Active employee");
        }
        if (activity.status().terminal()) {
            throw new ResourceStateConflictException(
                    "Terminal activity cannot receive new assignments");
        }
    }

    private ActivityAssignmentRecord mutationFailure(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion) {
        ActivityAssignmentRecord current = store.findById(scope, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity assignment"));
        requireVersion(expectedVersion, current);
        throw new ResourceStateConflictException("Activity assignment is already revoked");
    }

    private ScopeContext activityScope(UUID activityId) {
        ActivityRecord activity = activities.getForManagement(activityId);
        return activities.requireFarmManagement(activity.farmId());
    }

    private void requireVersion(long expectedVersion, ActivityAssignmentRecord current) {
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            ActivityAssignmentRecord assignment,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.ACTIVITY_ASSIGNMENT,
                Optional.of(assignment.id()),
                Optional.of(assignment.activityId() + ":" + assignment.employeeId()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "assignmentId is required");
    }
}
