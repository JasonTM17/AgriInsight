package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityLogReadService {

    private final PermissionEvaluator permissions;
    private final ActivityLogStore logs;
    private final ActivityLogReadStore reads;

    public ActivityLogReadService(
            PermissionEvaluator permissions,
            ActivityLogStore logs,
            ActivityLogReadStore reads) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.logs = Objects.requireNonNull(logs, "logs is required");
        this.reads = Objects.requireNonNull(reads, "reads is required");
    }

    public ActivityLogPage list(UUID activityId, ActivityReadPageQuery query) {
        UUID target = requiredId(activityId, "activityId");
        ScopeContext scope = readScope(target);
        ActivityLogAccess access = requiredAccess(scope, target);
        return reads.findAll(scope, target, access, Objects.requireNonNull(query, "query is required"));
    }

    public ActivityLogPage history(
            UUID activityId,
            UUID logId,
            ActivityReadPageQuery query) {
        UUID targetActivity = requiredId(activityId, "activityId");
        UUID targetLog = requiredId(logId, "logId");
        ScopeContext scope = readScope(targetActivity);
        ActivityLogAccess access = requiredAccess(scope, targetActivity);
        ActivityLogRecord selected = logs.findById(scope, targetActivity, targetLog)
                .filter(log -> visible(scope, access, log))
                .orElseThrow(() -> new ResourceNotFoundException("Activity log"));
        return reads.findHistory(
                scope,
                targetActivity,
                selected.id(),
                access,
                Objects.requireNonNull(query, "query is required"));
    }

    private ScopeContext readScope(UUID activityId) {
        return permissions.requireDomain(
                Permission.ACTIVITY_READ, ScopeContext.Type.ACTIVITY, activityId);
    }

    private ActivityLogAccess requiredAccess(ScopeContext scope, UUID activityId) {
        return logs.resolveAccess(scope, activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity"));
    }

    private boolean visible(
            ScopeContext scope,
            ActivityLogAccess access,
            ActivityLogRecord log) {
        return access.manager()
                || log.authorProfileId().equals(scope.profileId())
                || access.workerEmployeeId().filter(log.employeeId()::equals).isPresent();
    }

    private UUID requiredId(UUID id, String fieldName) {
        return Objects.requireNonNull(id, fieldName + " is required");
    }
}
