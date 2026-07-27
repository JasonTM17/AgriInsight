package com.agriinsight.backend.integration.application;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record OperationalEventRecord(String key, String valueJson, Map<String, String> headers) {

    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "event_id",
            "tenant_id",
            "command_id",
            "event_ordinal",
            "aggregate",
            "aggregate_id",
            "aggregate_version",
            "business_code",
            "event_type",
            "schema_version",
            "occurred_at",
            "payload");

    public OperationalEventRecord {
        key = requireText(key, "key");
        valueJson = requireText(valueJson, "valueJson");
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(headers, "headers are required")));
    }

    public static OperationalEventRecord from(
            OutboxEvent event, JsonMapper jsonMapper, int maxRecordBytes) {
        OutboxEvent required = Objects.requireNonNull(event, "event is required");
        JsonMapper mapper = Objects.requireNonNull(jsonMapper, "jsonMapper is required");
        byte[] value = required.payloadJson().getBytes(StandardCharsets.UTF_8);
        if (value.length > maxRecordBytes) {
            throw new IllegalArgumentException("event value exceeds the configured maximum");
        }

        JsonNode root = parseObject(mapper, required.payloadJson());
        if (!root.propertyNames().equals(ENVELOPE_FIELDS)) {
            throw new IllegalArgumentException("event envelope fields do not match schema v1");
        }
        requireEqual(root, "event_id", required.id().toString());
        requireEqual(root, "tenant_id", required.tenantId().toString());
        requireEqual(root, "command_id", required.commandId().toString());
        requireEqual(root, "aggregate", required.aggregateType());
        requireEqual(root, "aggregate_id", required.aggregateId().toString());
        requireEqual(root, "event_type", required.eventType());
        requireEqual(root, "occurred_at", required.occurredAt().toString());
        requireNumber(root, "event_ordinal", required.eventOrdinal());
        requireNumber(root, "aggregate_version", required.aggregateVersion());
        requireNumber(root, "schema_version", required.schemaVersion());
        if (!root.get("payload").isObject()) {
            throw new IllegalArgumentException("event payload must be an object");
        }
        JsonNode businessCode = root.get("business_code");
        if (!businessCode.isNull() && !businessCode.isString()) {
            throw new IllegalArgumentException("event business_code must be a string or null");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("agriinsight-event-id", required.id().toString());
        headers.put("agriinsight-tenant-id", required.tenantId().toString());
        headers.put("agriinsight-event-type", required.eventType());
        headers.put("agriinsight-schema-version", Integer.toString(required.schemaVersion()));
        return new OperationalEventRecord(
                required.tenantId() + ":" + required.aggregateType() + ":" + required.aggregateId(),
                required.payloadJson(),
                headers);
    }

    private static JsonNode parseObject(JsonMapper mapper, String value) {
        try {
            JsonNode root = mapper.readTree(value);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("event envelope must be a JSON object");
            }
            return root;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("event envelope must be valid JSON", exception);
        }
    }

    private static void requireEqual(JsonNode root, String field, String expected) {
        JsonNode value = root.get(field);
        if (value == null || !value.isString() || !expected.equals(value.asString())) {
            throw new IllegalArgumentException("event " + field + " does not match the outbox row");
        }
    }

    private static void requireNumber(JsonNode root, String field, long expected) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || value.asLong() != expected) {
            throw new IllegalArgumentException("event " + field + " does not match the outbox row");
        }
    }

    private static String requireText(String value, String name) {
        String required = Objects.requireNonNull(value, name + " is required");
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }
}
