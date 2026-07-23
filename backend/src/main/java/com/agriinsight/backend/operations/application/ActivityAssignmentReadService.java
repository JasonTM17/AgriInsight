package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
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
public class ActivityAssignmentReadService {

    private final PermissionEvaluator permissions;
    private final ActivityLogStore activityAccess;
    private final ActivityAssignmentReadStore assignments;

    public ActivityAssignmentReadService(
            PermissionEvaluator permissions,
            ActivityLogStore activityAccess,
            ActivityAssignmentReadStore assignments) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.activityAccess = Objects.requireNonNull(activityAccess, "activityAccess is required");
        this.assignments = Objects.requireNonNull(assignments, "assignments is required");
    }

    public ActivityAssignmentPage list(UUID activityId, ActivityReadPageQuery query) {
        UUID target = Objects.requireNonNull(activityId, "activityId is required");
        ScopeContext scope = permissions.requireDomain(
                Permission.ACTIVITY_READ, ScopeContext.Type.ACTIVITY, target);
        ActivityLogAccess access = activityAccess.resolveAccess(scope, target)
                .orElseThrow(() -> new ResourceNotFoundException("Activity"));
        Optional<UUID> employee = access.manager() ? Optional.empty() : access.workerEmployeeId();
        return assignments.findAll(
                scope, target, employee, Objects.requireNonNull(query, "query is required"));
    }
}
