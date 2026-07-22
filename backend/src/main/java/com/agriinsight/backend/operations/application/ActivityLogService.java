package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.operations.domain.ActivityLog;
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
public class ActivityLogService {

    private final PermissionEvaluator permissions;
    private final ActivityLogStore store;
    private final TenantAuditPublisher auditPublisher;

    public ActivityLogService(
            PermissionEvaluator permissions,
            ActivityLogStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public ActivityLogRecord append(UUID activityId, ActivityLogCommands.Append command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireScope(activityId);
        ActivityLogAccess access = requiredAccess(scope, activityId);
        requireEmployee(access, command.employeeId());
        ActivityLogRecord created = store.append(scope, new ActivityLog(
                UUID.randomUUID(), scope.tenantId(), requiredId(activityId), command.employeeId(),
                scope.profileId(), command.occurredAt(), command.notes(), command.quantity(),
                command.unit(), command.evidenceUri(), Optional.empty(), Optional.empty(), Optional.empty()))
                .orElseThrow(() -> appendConflict(access));
        publish(scope, TenantAuditEvent.Action.ACTIVITY_LOG_APPENDED, created, command.audit());
        return created;
    }

    public ActivityLogRecord correct(
            UUID activityId,
            UUID logId,
            ActivityLogCommands.Correct command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireScope(activityId);
        ActivityLogAccess access = requiredAccess(scope, activityId);
        ActivityLogRecord original = store.findById(scope, requiredId(activityId), requiredId(logId))
                .orElseThrow(() -> new ResourceNotFoundException("Activity log"));
        requireCorrectionOwnership(scope, access, original);
        ActivityLogRecord correction = store.append(scope, new ActivityLog(
                UUID.randomUUID(), scope.tenantId(), original.activityId(), original.employeeId(),
                scope.profileId(), command.occurredAt(), command.notes(), command.quantity(),
                command.unit(), command.evidenceUri(), Optional.of(original.id()),
                Optional.of(command.correctionKind()), Optional.of(command.correctionReason())))
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Activity log correction target is unavailable or already corrected"));
        publish(scope, TenantAuditEvent.Action.ACTIVITY_LOG_CORRECTED, correction, command.audit());
        return correction;
    }

    ScopeContext requireScope(UUID activityId) {
        return permissions.requireDomain(
                Permission.ACTIVITY_LOG_APPEND,
                ScopeContext.Type.ACTIVITY,
                requiredId(activityId));
    }

    ActivityLogAccess requiredAccess(ScopeContext scope, UUID activityId) {
        return store.resolveAccess(scope, requiredId(activityId))
                .orElseThrow(() -> new ResourceNotFoundException("Activity"));
    }

    ActivityLogRecord getForReplay(UUID activityId, UUID logId) {
        ScopeContext scope = requireScope(activityId);
        ActivityLogAccess access = requiredAccess(scope, activityId);
        ActivityLogRecord log = store.findById(scope, requiredId(activityId), requiredId(logId))
                .orElseThrow(() -> new ResourceNotFoundException("Activity log"));
        if (!access.manager() && !log.authorProfileId().equals(scope.profileId())) {
            throw new ResourceNotFoundException("Activity log");
        }
        return log;
    }

    void requireAppendTarget(UUID activityId, UUID employeeId) {
        ScopeContext scope = requireScope(activityId);
        requireEmployee(requiredAccess(scope, activityId), requiredId(employeeId));
    }

    void requireCorrectionTarget(UUID activityId, UUID logId) {
        ScopeContext scope = requireScope(activityId);
        ActivityLogAccess access = requiredAccess(scope, activityId);
        ActivityLogRecord original = store.findById(scope, requiredId(activityId), requiredId(logId))
                .orElseThrow(() -> new ResourceNotFoundException("Activity log"));
        requireCorrectionOwnership(scope, access, original);
    }

    private void requireEmployee(ActivityLogAccess access, UUID employeeId) {
        if (!access.manager()
                && !access.workerEmployeeId().filter(employeeId::equals).isPresent()) {
            throw new ResourceNotFoundException("Active activity assignment");
        }
    }

    private void requireCorrectionOwnership(
            ScopeContext scope,
            ActivityLogAccess access,
            ActivityLogRecord original) {
        if (!access.manager()
                && (!original.authorProfileId().equals(scope.profileId())
                || !access.workerEmployeeId().filter(original.employeeId()::equals).isPresent())) {
            throw new ResourceNotFoundException("Activity log");
        }
    }

    private RuntimeException appendConflict(ActivityLogAccess access) {
        return access.manager()
                ? new ResourceStateConflictException(
                        "Activity log requires an active employee assignment")
                : new ResourceNotFoundException("Active activity assignment");
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            ActivityLogRecord log,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.ACTIVITY_LOG,
                Optional.of(log.id()),
                Optional.of(log.activityId() + ":" + log.employeeId()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID id) {
        return Objects.requireNonNull(id, "id is required");
    }
}
