package com.agriinsight.backend.operations.domain;

import java.util.Objects;

public enum ActivityStatus {
    PLANNED,
    STARTED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(ActivityStatus target) {
        Objects.requireNonNull(target, "targetStatus is required");
        return switch (this) {
            case PLANNED -> target == STARTED || target == CANCELLED;
            case STARTED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
