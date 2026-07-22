package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.ActivityLog;
import java.util.Optional;
import java.util.UUID;

public interface ActivityLogStore {

    Optional<ActivityLogAccess> resolveAccess(ScopeContext scope, UUID activityId);

    Optional<ActivityLogRecord> findById(
            ScopeContext scope,
            UUID activityId,
            UUID logId);

    Optional<ActivityLogRecord> append(ScopeContext scope, ActivityLog log);
}
