package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Validates the immutable JSON value of an operational event without retaining its payload. */
final class RealtimeEventEnvelopeParser {

    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "event_id", "tenant_id", "command_id", "event_ordinal", "aggregate",
            "aggregate_id", "aggregate_version", "business_code", "event_type",
            "schema_version", "occurred_at", "payload");
    private static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern EVENT_TYPE = Pattern.compile(
            "AGRIINSIGHT\\.OPERATIONAL\\.([A-Z][A-Z0-9_]{0,63})\\.COMMITTED");

    private final JsonMapper jsonMapper;

    RealtimeEventEnvelopeParser(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper is required")
                .rebuild()
                .enable(
                        DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    RealtimeEventEnvelope parse(String value) {
        JsonNode envelope = parseObject(value);
        UUID eventId = requireUuid(envelope, "event_id");
        UUID tenantId = requireUuid(envelope, "tenant_id");
        requireUuid(envelope, "command_id");
        requireNonNegativeInt(envelope, "event_ordinal");
        String aggregateType = requireAggregateType(envelope);
        UUID aggregateId = requireUuid(envelope, "aggregate_id");
        long aggregateVersion = requireNonNegativeLong(envelope, "aggregate_version");
        requireBusinessCode(envelope);
        String eventType = requireEventType(envelope, aggregateType);
        requireSchemaVersion(envelope);
        Instant occurredAt = requireInstant(envelope, "occurred_at");
        if (!envelope.required("payload").isObject()) {
            throw invalid("event payload must be an object");
        }
        return new RealtimeEventEnvelope(
                eventId, tenantId, aggregateType, aggregateId, aggregateVersion, eventType, occurredAt);
    }

    private JsonNode parseObject(String value) {
        try {
            JsonNode root = jsonMapper.readTree(value);
            if (root == null || !root.isObject() || !root.propertyNames().equals(ENVELOPE_FIELDS)) {
                throw invalid("event envelope does not match schema v1");
            }
            return root;
        } catch (JacksonException exception) {
            throw invalid("event envelope must be valid schema v1 JSON", exception);
        }
    }

    private static UUID requireUuid(JsonNode root, String field) {
        String value = requireText(root, field, 36);
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid("event " + field + " must be a canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw invalid("event " + field + " must be a canonical UUID", exception);
        }
    }

    private static int requireNonNegativeInt(JsonNode root, String field) {
        JsonNode value = root.required(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) {
            throw invalid("event " + field + " must be a non-negative integer");
        }
        return value.asInt();
    }

    private static long requireNonNegativeLong(JsonNode root, String field) {
        JsonNode value = root.required(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) {
            throw invalid("event " + field + " must be a non-negative integer");
        }
        return value.asLong();
    }

    private static String requireAggregateType(JsonNode root) {
        String value = requireText(root, "aggregate", 64);
        if (!AGGREGATE_TYPE.matcher(value).matches()) {
            throw invalid("event aggregate has an invalid format");
        }
        return value;
    }

    private static void requireBusinessCode(JsonNode root) {
        JsonNode value = root.required("business_code");
        if (!value.isNull() && (!value.isString() || value.stringValue().length() > 128)) {
            throw invalid("event business_code must be a string or null");
        }
    }

    private static String requireEventType(JsonNode root, String aggregateType) {
        String value = requireText(root, "event_type", 160);
        Matcher matcher = EVENT_TYPE.matcher(value);
        if (!matcher.matches() || !matcher.group(1).equals(aggregateType)) {
            throw invalid("event type does not match its aggregate");
        }
        return value;
    }

    private static void requireSchemaVersion(JsonNode root) {
        if (requireNonNegativeInt(root, "schema_version") != 1) {
            throw invalid("event schema_version must equal 1");
        }
    }

    private static Instant requireInstant(JsonNode root, String field) {
        try {
            return Instant.parse(requireText(root, field, 64));
        } catch (DateTimeParseException exception) {
            throw invalid("event " + field + " must be an ISO-8601 instant", exception);
        }
    }

    private static String requireText(JsonNode root, String field, int maximumLength) {
        JsonNode value = root.required(field);
        if (!value.isString() || value.stringValue().isBlank() || value.stringValue().length() > maximumLength) {
            throw invalid("event " + field + " must be a bounded non-blank string");
        }
        return value.stringValue();
    }

    private static RealtimeEventValidationException invalid(String message) {
        return new RealtimeEventValidationException(message);
    }

    private static RealtimeEventValidationException invalid(String message, Throwable cause) {
        return new RealtimeEventValidationException(message, cause);
    }
}
