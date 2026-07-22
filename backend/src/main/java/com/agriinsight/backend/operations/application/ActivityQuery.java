package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ActivityQuery(
        int limit,
        int offset,
        Optional<UUID> farmId,
        Optional<UUID> fieldId,
        Optional<UUID> seasonId,
        Optional<ActivityType> activityType,
        Optional<ActivityStatus> status,
        Optional<String> search) {

    public ActivityQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
        farmId = Objects.requireNonNull(farmId, "farmId is required");
        fieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        seasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        activityType = Objects.requireNonNull(activityType, "activityType is required");
        status = Objects.requireNonNull(status, "status is required");
        search = Objects.requireNonNull(search, "search is required")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        search.ifPresent(value -> {
            if (value.length() > 120) {
                throw new IllegalArgumentException("search must not exceed 120 characters");
            }
        });
    }
}
