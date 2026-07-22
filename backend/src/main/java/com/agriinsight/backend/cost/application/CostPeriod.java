package com.agriinsight.backend.cost.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

final class CostPeriod {

    static final Duration MAXIMUM = Duration.ofDays(366);

    private CostPeriod() {
    }

    static void requireBounded(Instant fromInclusive, Instant toExclusive) {
        Instant from = Objects.requireNonNull(fromInclusive, "occurredFrom is required");
        Instant to = Objects.requireNonNull(toExclusive, "occurredTo is required");
        Duration range = Duration.between(from, to);
        if (range.isZero() || range.isNegative()) {
            throw new IllegalArgumentException("occurredTo must be after occurredFrom");
        }
        if (range.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("Cost queries cannot exceed 366 days");
        }
    }
}
