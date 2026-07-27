package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.UUID;

/** Parsed schema-v1 metadata before Kafka headers and coordinates are validated. */
record RealtimeEventEnvelope(
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        Instant occurredAt) {
}
