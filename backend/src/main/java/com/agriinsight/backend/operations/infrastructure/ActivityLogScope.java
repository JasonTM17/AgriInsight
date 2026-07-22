package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Objects;
import java.util.UUID;

final class ActivityLogScope {

    private ActivityLogScope() {
    }

    static ScopeContext require(ScopeContext scope, UUID activityId) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        UUID requiredActivityId = Objects.requireNonNull(activityId, "activityId is required");
        if (required.type() == ScopeContext.Type.TENANT && required.resourceId().isEmpty()) {
            return required;
        }
        if (required.type() != ScopeContext.Type.ACTIVITY
                || required.resourceId().isEmpty()
                || !required.resourceId().orElseThrow().equals(requiredActivityId)) {
            throw new IllegalArgumentException(
                    "Activity log store requires tenant or target activity scope");
        }
        return required;
    }
}
