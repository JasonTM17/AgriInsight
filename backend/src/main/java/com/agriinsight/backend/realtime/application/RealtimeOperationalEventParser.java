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
    private static final int MAX_KEY_BYTES = 138;
    private static final int MAX_HEADER_NAME_BYTES = 64;
    private static final int MAX_HEADER_VALUE_BYTES = 160;

    private final RealtimeEventEnvelopeParser envelopeParser;

    public RealtimeOperationalEventParser(JsonMapper jsonMapper) {
        this.envelopeParser = new RealtimeEventEnvelopeParser(
                Objects.requireNonNull(jsonMapper, "jsonMapper is required"));
    }

    public RealtimeOperationalEvent parse(ConsumerRecord<byte[], byte[]> record, int maxRecordBytes) {
        ConsumerRecord<byte[], byte[]> required = Objects.requireNonNull(record, "record is required");
        if (maxRecordBytes < 1) {
            throw new IllegalArgumentException("maxRecordBytes must be positive");
        }
        byte[] keyBytes = Objects.requireNonNull(required.key(), "record key is required");
        byte[] valueBytes = Objects.requireNonNull(required.value(), "record value is required");
        requireBoundedInput(required, keyBytes, valueBytes, maxRecordBytes);
        String value = decodeUtf8(valueBytes, "event value");

        RealtimeEventEnvelope envelope = envelopeParser.parse(value);

        Map<String, String> headers = requireHeaders(required);
        requireHeader(headers, "agriinsight-event-id", envelope.eventId().toString());
        requireHeader(headers, "agriinsight-tenant-id", envelope.tenantId().toString());
        requireHeader(headers, "agriinsight-event-type", envelope.eventType());
        requireHeader(headers, "agriinsight-schema-version", "1");
        requireKey(
                decodeUtf8(keyBytes, "event key"),
                envelope.tenantId(),
                envelope.aggregateType(),
                envelope.aggregateId());

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

    private static void requireBoundedInput(
            ConsumerRecord<byte[], byte[]> record,
            byte[] keyBytes,
            byte[] valueBytes,
            int maximumBytes) {
        if (keyBytes.length > MAX_KEY_BYTES) {
            throw invalid("event key exceeds the configured maximum");
        }
        long inputBytes = (long) keyBytes.length + valueBytes.length;
        for (Header header : record.headers()) {
            String name = header.key();
            if (name == null) {
                throw invalid("event header name is required");
            }
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length > MAX_HEADER_NAME_BYTES) {
                throw invalid("event header name exceeds the configured maximum");
            }
            byte[] headerValue = header.value();
            if (headerValue == null) {
                throw invalid("event header value is required");
            }
            if (headerValue.length > MAX_HEADER_VALUE_BYTES) {
                throw invalid("event header value exceeds the configured maximum");
            }
            inputBytes += nameBytes.length + headerValue.length;
            if (inputBytes > maximumBytes) {
                throw invalid("event input exceeds the configured maximum");
            }
        }
        if (inputBytes > maximumBytes) {
            throw invalid("event input exceeds the configured maximum");
        }
    }

    private static Map<String, String> requireHeaders(ConsumerRecord<byte[], byte[]> record) {
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
        byte[] value = header.value();
        if (value == null) {
            throw invalid("event headers must contain UTF-8 text");
        }
        return decodeUtf8(value, "event headers");
    }

    private static String decodeUtf8(byte[] value, String field) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalid(field + " must contain UTF-8 text", exception);
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
