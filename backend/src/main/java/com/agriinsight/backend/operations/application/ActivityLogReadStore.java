package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.UUID;

public interface ActivityLogReadStore {

    ActivityLogPage findAll(
            ScopeContext scope,
            UUID activityId,
            ActivityLogAccess access,
            ActivityReadPageQuery query);

    ActivityLogPage findHistory(
            ScopeContext scope,
            UUID activityId,
            UUID logId,
            ActivityLogAccess access,
            ActivityReadPageQuery query);
}
