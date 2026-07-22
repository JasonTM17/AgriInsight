package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ActivityRecord(
        UUID id,
        UUID tenantId,
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

    public ActivityRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        Objects.requireNonNull(activityType, "activityType is required");
        code = Activity.canonicalCode(code);
        title = Activity.canonicalTitle(title);
        description = Activity.optionalDescription(description);
        Activity.requireSchedule(plannedStartAt, dueAt);
        startedAt = Objects.requireNonNull(startedAt, "startedAt is required");
        completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
        cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt is required");
        status = Objects.requireNonNull(status, "status is required");
        requireStatusTimes(status, startedAt, completedAt, cancelledAt);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    private static void requireStatusTimes(
            ActivityStatus status,
            Optional<Instant> startedAt,
            Optional<Instant> completedAt,
            Optional<Instant> cancelledAt) {
        boolean valid = switch (status) {
            case PLANNED -> startedAt.isEmpty() && completedAt.isEmpty() && cancelledAt.isEmpty();
            case STARTED -> startedAt.isPresent() && completedAt.isEmpty() && cancelledAt.isEmpty();
            case COMPLETED -> startedAt.isPresent() && completedAt.isPresent() && cancelledAt.isEmpty();
            case CANCELLED -> completedAt.isEmpty() && cancelledAt.isPresent();
        };
        if (!valid || completedBeforeStart(startedAt, completedAt)
                || cancelledBeforeStart(startedAt, cancelledAt)) {
            throw new IllegalArgumentException("activity status times are invalid");
        }
    }

    private static boolean completedBeforeStart(
            Optional<Instant> startedAt,
            Optional<Instant> completedAt) {
        return startedAt.isPresent() && completedAt.isPresent()
                && completedAt.orElseThrow().isBefore(startedAt.orElseThrow());
    }

    private static boolean cancelledBeforeStart(
            Optional<Instant> startedAt,
            Optional<Instant> cancelledAt) {
        return startedAt.isPresent() && cancelledAt.isPresent()
                && cancelledAt.orElseThrow().isBefore(startedAt.orElseThrow());
    }
}
