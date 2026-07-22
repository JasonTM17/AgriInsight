package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityRecord;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID farmId,
        UUID fieldId,
        UUID seasonId,
        ActivityType activityType,
        String code,
        String title,
        Optional<String> description,
        Instant plannedStartAt,
        Instant dueAt,
        Optional<Instant> startedAt,
        Optional<Instant> completedAt,
        Optional<Instant> cancelledAt,
        ActivityStatus status,
        long version) {

    public ActivityResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        Objects.requireNonNull(activityType, "activityType is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(plannedStartAt, "plannedStartAt is required");
        Objects.requireNonNull(dueAt, "dueAt is required");
        Objects.requireNonNull(startedAt, "startedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        Objects.requireNonNull(cancelledAt, "cancelledAt is required");
        Objects.requireNonNull(status, "status is required");
    }

    public static ActivityResponse from(ActivityRecord activity) {
        Objects.requireNonNull(activity, "activity is required");
        return new ActivityResponse(
                activity.id(), activity.farmId(), activity.fieldId(), activity.seasonId(),
                activity.activityType(), activity.code(), activity.title(), activity.description(),
                activity.plannedStartAt(), activity.dueAt(), activity.startedAt(),
                activity.completedAt(), activity.cancelledAt(), activity.status(), activity.version());
    }
}
