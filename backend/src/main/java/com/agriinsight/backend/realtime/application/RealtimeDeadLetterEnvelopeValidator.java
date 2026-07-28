package com.agriinsight.backend.realtime.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import tools.jackson.databind.json.JsonMapper;

/** Parses only a bounded DLT value; Kafka headers and key are deliberately ignored. */
public class RealtimeDeadLetterEnvelopeValidator {

    private final RealtimeEventEnvelopeParser envelopeParser;

    public RealtimeDeadLetterEnvelopeValidator(JsonMapper jsonMapper) {
        this.envelopeParser = new RealtimeEventEnvelopeParser(
                Objects.requireNonNull(jsonMapper, "jsonMapper is required"));
    }

    public RealtimeOperationalEvent parse(ConsumerRecord<byte[], byte[]> record, int maxRecordBytes) {
        ConsumerRecord<byte[], byte[]> required = Objects.requireNonNull(record, "record is required");
        if (maxRecordBytes < 1) {
            throw new IllegalArgumentException("maxRecordBytes must be positive");
        }
        byte[] value = required.value();
        if (value == null) {
            throw invalid("DLT event value is required");
        }
        if (value.length > maxRecordBytes) {
            throw invalid("DLT event value exceeds the configured maximum");
        }
        RealtimeEventEnvelope envelope = envelopeParser.parse(decodeUtf8(value));
        return new RealtimeOperationalEvent(
                envelope.eventId(),
                envelope.tenantId(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.aggregateVersion(),
                envelope.eventType(),
                envelope.occurredAt(),
                sha256(value),
                required.topic(),
                required.partition(),
                required.offset());
    }

    private static String decodeUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalid("DLT event value must contain UTF-8 text", exception);
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
