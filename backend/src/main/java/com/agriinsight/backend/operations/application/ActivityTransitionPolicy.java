package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.time.Instant;
import java.util.Objects;

final class ActivityTransitionPolicy {

    private ActivityTransitionPolicy() {
    }

    static void requireMutable(ActivityRecord activity) {
        ActivityRecord required = Objects.requireNonNull(activity, "activity is required");
        if (required.status().terminal()) {
            throw new ResourceStateConflictException("Terminal activity metadata is immutable");
        }
    }

    static void validate(
            ActivityRecord current,
            ActivityStatus target,
            Instant effectiveAt) {
        ActivityRecord required = Objects.requireNonNull(current, "current activity is required");
        ActivityStatus requiredTarget = Objects.requireNonNull(target, "targetStatus is required");
        Instant requiredTime = Objects.requireNonNull(effectiveAt, "effectiveAt is required");
        if (!required.status().canTransitionTo(requiredTarget)) {
            throw new ResourceStateConflictException("Activity transition is not allowed");
        }
        if (required.startedAt().isPresent()
                && requiredTime.isBefore(required.startedAt().orElseThrow())) {
            throw new ResourceStateConflictException(
                    "Activity effective time cannot precede its start time");
        }
    }
}
