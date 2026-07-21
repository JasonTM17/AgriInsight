package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.time.LocalDate;
import java.util.Objects;

final class SeasonTransitionPolicy {

    private SeasonTransitionPolicy() {
    }

    static void requireMutable(SeasonRecord season) {
        SeasonRecord required = Objects.requireNonNull(season, "season is required");
        if (required.status().terminal()) {
            throw new ResourceStateConflictException("Terminal season metadata is immutable");
        }
    }

    static void validate(
            SeasonRecord current,
            Season.Status target,
            LocalDate effectiveDate) {
        SeasonRecord required = Objects.requireNonNull(current, "current season is required");
        Season.Status requiredTarget = Objects.requireNonNull(target, "targetStatus is required");
        LocalDate requiredDate = Objects.requireNonNull(effectiveDate, "effectiveDate is required");
        if (!required.status().canTransitionTo(requiredTarget)) {
            throw new ResourceStateConflictException("Season transition is not allowed");
        }
        if (required.startedOn().isPresent()
                && requiredDate.isBefore(required.startedOn().orElseThrow())) {
            throw new ResourceStateConflictException("Season effective date cannot precede its start date");
        }
    }
}
