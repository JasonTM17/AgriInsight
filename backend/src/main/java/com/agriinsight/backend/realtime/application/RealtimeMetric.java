package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;

/** One bounded, payload-free metric projected from committed operational events. */
public record RealtimeMetric(
        String eventType,
        String aggregateType,
        long eventCount,
        Instant lastOccurredAt,
        Instant lastProcessedAt) {

    public RealtimeMetric {
        eventType = requireText(eventType, "eventType");
        aggregateType = requireText(aggregateType, "aggregateType");
        if (eventCount < 0) {
            throw new IllegalArgumentException("eventCount must not be negative");
        }
        Objects.requireNonNull(lastOccurredAt, "lastOccurredAt is required");
        Objects.requireNonNull(lastProcessedAt, "lastProcessedAt is required");
    }

    private static String requireText(String value, String field) {
        String required = Objects.requireNonNull(value, field + " is required");
        if (required.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return required;
    }
}
