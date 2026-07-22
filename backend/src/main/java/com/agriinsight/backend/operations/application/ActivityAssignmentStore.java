package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.ActivityAssignment;
import java.util.Optional;
import java.util.UUID;

public interface ActivityAssignmentStore {

    Optional<ActivityAssignmentRecord> findById(ScopeContext scope, UUID assignmentId);

    Optional<ActivityAssignmentRecord> findActive(
            ScopeContext scope,
            UUID activityId,
            UUID employeeId);

    boolean activeEmployeeExists(ScopeContext scope, UUID employeeId);

    Optional<ActivityAssignmentRecord> create(ScopeContext scope, ActivityAssignment assignment);

    Optional<ActivityAssignmentRecord> revoke(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion);
}
