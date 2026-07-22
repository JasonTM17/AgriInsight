package com.agriinsight.backend.shared.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal transaction event; integration persists it before the transaction commits. */
public record CommandCommittedEvent(
        UUID tenantId,
        UUID principalId,
        UUID commandId,
        String routeTemplate,
        CommandTarget target,
        Optional<String> correlationId,
        Instant occurredAt,
        int eventOrdinal) {

    public static final int SCHEMA_VERSION = 1;

    public CommandCommittedEvent {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principalId, "principalId is required");
        Objects.requireNonNull(commandId, "commandId is required");
        routeTemplate = Objects.requireNonNull(routeTemplate, "routeTemplate is required");
        if (routeTemplate.isBlank()) {
            throw new IllegalArgumentException("routeTemplate must not be blank");
        }
        Objects.requireNonNull(target, "target is required");
        correlationId = Objects.requireNonNull(correlationId, "correlationId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (eventOrdinal < 0) {
            throw new IllegalArgumentException("eventOrdinal must not be negative");
        }
    }

    public String eventType() {
        return "AGRIINSIGHT.OPERATIONAL." + target.resourceType() + ".COMMITTED";
    }

    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resource_type", target.resourceType());
        payload.put("resource_id", target.resourceId().toString());
        payload.put("resource_version", target.resourceVersion());
        payload.put("route_template", routeTemplate);
        payload.put("correlation_id", correlationId.orElse(null));
        return java.util.Collections.unmodifiableMap(payload);
    }
}
