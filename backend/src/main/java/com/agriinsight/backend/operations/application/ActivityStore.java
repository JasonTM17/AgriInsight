package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActivityStore {

    ActivityPage findAll(ScopeContext scope, ActivityQuery query);

    Optional<ActivityRecord> findById(ScopeContext scope, UUID activityId);

    boolean farmVisible(ScopeContext scope, UUID farmId);

    boolean liveParentsAvailable(
            ScopeContext scope,
            UUID farmId,
            UUID fieldId,
            UUID seasonId,
            ActivityType activityType);

    Optional<ActivityRecord> create(ScopeContext scope, Activity activity);

    Optional<ActivityRecord> update(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion,
            ActivityCommands.Update command);

    Optional<ActivityRecord> transition(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion,
            ActivityStatus sourceStatus,
            ActivityStatus targetStatus,
            Instant effectiveAt);
}
