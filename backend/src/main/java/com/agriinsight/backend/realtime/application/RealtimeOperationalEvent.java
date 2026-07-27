package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Metadata retained from a validated operational event; the source payload is intentionally omitted. */
public record RealtimeOperationalEvent(
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        Instant occurredAt,
        String checksum,
        String topic,
        int partition,
        long offset) {

    private static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern EVENT_TYPE = Pattern.compile(
            "AGRIINSIGHT\\.OPERATIONAL\\.[A-Z][A-Z0-9_]{0,63}\\.COMMITTED");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public RealtimeOperationalEvent {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        aggregateType = requirePattern(aggregateType, AGGREGATE_TYPE, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        eventType = requirePattern(eventType, EVENT_TYPE, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        checksum = requirePattern(checksum, CHECKSUM, "checksum");
        topic = requireTopic(topic);
        if (partition < 0 || offset < 0) {
            throw new IllegalArgumentException("Kafka coordinates must not be negative");
        }
    }

    private static String requirePattern(String value, Pattern pattern, String field) {
        String required = Objects.requireNonNull(value, field + " is required");
        if (!pattern.matcher(required).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return required;
    }

    private static String requireTopic(String value) {
        String required = Objects.requireNonNull(value, "topic is required");
        if (required.isBlank() || required.length() > 249) {
            throw new IllegalArgumentException("topic has an invalid format");
        }
        return required;
    }
}
