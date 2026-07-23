package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.FarmAssignment;
import java.util.Optional;
import java.util.UUID;

public interface FarmAssignmentStore {

    FarmAssignmentPage findAll(ScopeContext scope, FarmAssignmentQuery query);

    Optional<FarmAssignmentRecord> findById(ScopeContext scope, UUID assignmentId);

    Optional<FarmAssignmentRecord> findActive(
            ScopeContext scope,
            UUID userProfileId,
            UUID farmId);

    boolean activeProfileExists(ScopeContext scope, UUID userProfileId);

    boolean activeFarmExists(ScopeContext scope, UUID farmId);

    FarmAssignmentRecord create(ScopeContext scope, FarmAssignment assignment);

    Optional<FarmAssignmentRecord> revoke(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion);
}
