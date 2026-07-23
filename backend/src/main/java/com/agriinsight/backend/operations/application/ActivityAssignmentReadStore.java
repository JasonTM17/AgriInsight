package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;

public interface ActivityAssignmentReadStore {

    ActivityAssignmentPage findAll(
            ScopeContext scope,
            UUID activityId,
            Optional<UUID> employeeId,
            ActivityReadPageQuery query);
}
