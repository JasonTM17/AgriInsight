package com.agriinsight.backend.realtime.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import tools.jackson.databind.json.JsonMapper;

/** Strictly parses the producer's schema-v1 Kafka record and discards its raw payload. */
public class RealtimeOperationalEventParser {

    private static final Set<String> HEADER_FIELDS = Set.of(
            "agriinsight-event-id", "agriinsight-tenant-id", "agriinsight-event-type",
            "agriinsight-schema-version");

    private final RealtimeEventEnvelopeParser envelopeParser;

    public RealtimeOperationalEventParser(JsonMapper jsonMapper) {
        this.envelopeParser = new RealtimeEventEnvelopeParser(
                Objects.requireNonNull(jsonMapper, "jsonMapper is required"));
    }

    public RealtimeOperationalEvent parse(ConsumerRecord<String, String> record, int maxRecordBytes) {
        ConsumerRecord<String, String> required = Objects.requireNonNull(record, "record is required");
        if (maxRecordBytes < 1) {
            throw new IllegalArgumentException("maxRecordBytes must be positive");
        }
        String value = Objects.requireNonNull(required.value(), "record value is required");
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        if (valueBytes.length > maxRecordBytes) {
            throw invalid("event value exceeds the configured maximum");
        }

        RealtimeEventEnvelope envelope = envelopeParser.parse(value);

        Map<String, String> headers = requireHeaders(required);
        requireHeader(headers, "agriinsight-event-id", envelope.eventId().toString());
        requireHeader(headers, "agriinsight-tenant-id", envelope.tenantId().toString());
        requireHeader(headers, "agriinsight-event-type", envelope.eventType());
        requireHeader(headers, "agriinsight-schema-version", "1");
        requireKey(required.key(), envelope.tenantId(), envelope.aggregateType(), envelope.aggregateId());

        return new RealtimeOperationalEvent(
                envelope.eventId(),
                envelope.tenantId(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.aggregateVersion(),
                envelope.eventType(),
                envelope.occurredAt(),
                sha256(valueBytes),
                required.topic(),
                required.partition(),
                required.offset());
    }

    private static Map<String, String> requireHeaders(ConsumerRecord<String, String> record) {
        Map<String, List<Header>> grouped = new HashMap<>();
        for (Header header : record.headers()) {
            grouped.computeIfAbsent(header.key(), ignored -> new java.util.ArrayList<>()).add(header);
        }
        if (!grouped.keySet().equals(HEADER_FIELDS)
                || grouped.values().stream().anyMatch(headers -> headers.size() != 1)) {
            throw invalid("event headers must exactly match schema v1 metadata");
        }
        Map<String, String> values = new HashMap<>();
        grouped.forEach((name, headers) -> values.put(name, decodeHeader(headers.get(0))));
        return values;
    }

    private static String decodeHeader(Header header) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Objects.requireNonNull(header.value(), "header value is required")))
                    .toString();
        } catch (CharacterCodingException | NullPointerException exception) {
            throw invalid("event headers must contain UTF-8 text", exception);
        }
    }

    private static void requireHeader(Map<String, String> headers, String name, String expected) {
        if (!expected.equals(headers.get(name))) {
            throw invalid("event header " + name + " does not match the value");
        }
    }

    private static void requireKey(String key, UUID tenantId, String aggregateType, UUID aggregateId) {
        String expected = tenantId + ":" + aggregateType + ":" + aggregateId;
        if (!expected.equals(key)) {
            throw invalid("event key does not match the value");
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static RealtimeEventValidationException invalid(String message) {
        return new RealtimeEventValidationException(message);
    }

    private static RealtimeEventValidationException invalid(String message, Throwable cause) {
        return new RealtimeEventValidationException(message, cause);
    }
}
