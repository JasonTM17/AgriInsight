package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RealtimeOperationalEventParserTest {

    private static final UUID EVENT_ID = UUID.fromString("77000000-0000-0000-0000-000000000020");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID AGGREGATE_ID = UUID.fromString("77000000-0000-0000-0000-000000000001");
    private static final String EVENT_TYPE = "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED";

    private final RealtimeOperationalEventParser parser =
            new RealtimeOperationalEventParser(new JsonMapper());

    @Test
    void parsesTheExactV1RecordWithoutRetainingPayload() throws Exception {
        ConsumerRecord<byte[], byte[]> record = record(value(), headers());

        RealtimeOperationalEvent event = parser.parse(record, 262_144);

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.aggregateType()).isEqualTo("FARM");
        assertThat(event.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(event.aggregateVersion()).isEqualTo(3);
        assertThat(event.eventType()).isEqualTo(EVENT_TYPE);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-27T13:30:00Z"));
        assertThat(event.checksum()).isEqualTo(sha256(value().getBytes(StandardCharsets.UTF_8)));
        assertThat(event.topic()).isEqualTo("agriinsight.operational.v1");
        assertThat(event.partition()).isZero();
        assertThat(event.offset()).isEqualTo(42);
        assertThat(event.toString()).doesNotContain("ignored-raw-payload");
    }

    @Test
    void rejectsMissingDuplicateAndMismatchedHeaders() {
        RecordHeaders missing = headers();
        missing.remove("agriinsight-event-id");
        assertThatThrownBy(() -> parser.parse(record(value(), missing), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("headers");

        RecordHeaders duplicate = headers();
        duplicate.add("agriinsight-event-id", EVENT_ID.toString().getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(record(value(), duplicate), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("headers");

        RecordHeaders mismatched = headers();
        mismatched.remove("agriinsight-event-type");
        mismatched.add("agriinsight-event-type", "AGRIINSIGHT.OPERATIONAL.CROP.COMMITTED"
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(record(value(), mismatched), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("agriinsight-event-type");
    }

    @Test
    void rejectsSchemaDriftKeyDriftAndOversizeRecordInputs() {
        assertThatThrownBy(() -> parser.parse(record(
                        value().replace("\"payload\"", "\"unexpected\""), headers()), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("schema v1");

        assertThatThrownBy(() -> parser.parse(record(
                        "other-tenant:FARM:" + AGGREGATE_ID, value(), headers()), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("key");

        assertThatThrownBy(() -> parser.parse(record(value(), headers()), 16))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void rejectsMalformedValueUtf8BeforeItsChecksumCanBeCalculated() {
        byte[] malformedValue = value().getBytes(StandardCharsets.UTF_8);
        malformedValue[value().indexOf("ignored-raw-payload")] = (byte) 0xFF;

        assertThatThrownBy(() -> parser.parse(record(
                        (TENANT_ID + ":FARM:" + AGGREGATE_ID).getBytes(StandardCharsets.UTF_8),
                        malformedValue,
                        headers()),
                        262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void rejectsOversizedKeysAndHeaderValuesBeforeDecodingThem() {
        assertThatThrownBy(() -> parser.parse(record(
                        new byte[139], value().getBytes(StandardCharsets.UTF_8), headers()), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("key");

        RecordHeaders oversizedHeader = headers();
        oversizedHeader.remove("agriinsight-event-id");
        oversizedHeader.add("agriinsight-event-id", new byte[161]);
        assertThatThrownBy(() -> parser.parse(record(value(), oversizedHeader), 262_144))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessageContaining("header");
    }

    private static ConsumerRecord<byte[], byte[]> record(String value, RecordHeaders headers) {
        return record(TENANT_ID + ":FARM:" + AGGREGATE_ID, value, headers);
    }

    private static ConsumerRecord<byte[], byte[]> record(
            String key, String value, RecordHeaders headers) {
        return record(
                key.getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8),
                headers);
    }

    private static ConsumerRecord<byte[], byte[]> record(
            byte[] key, byte[] value, RecordHeaders headers) {
        return new ConsumerRecord<>(
                "agriinsight.operational.v1",
                0,
                42,
                ConsumerRecord.NO_TIMESTAMP,
                TimestampType.CREATE_TIME,
                key.length,
                value.length,
                key,
                value,
                headers,
                Optional.empty(),
                Optional.empty());
    }

    private static RecordHeaders headers() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("agriinsight-event-id", EVENT_ID.toString().getBytes(StandardCharsets.UTF_8));
        headers.add("agriinsight-tenant-id", TENANT_ID.toString().getBytes(StandardCharsets.UTF_8));
        headers.add("agriinsight-event-type", EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        headers.add("agriinsight-schema-version", "1".getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    private static String value() {
        return """
                {"event_id":"77000000-0000-0000-0000-000000000020","tenant_id":"10000000-0000-0000-0000-000000000041","command_id":"77000000-0000-0000-0000-000000000010","event_ordinal":0,"aggregate":"FARM","aggregate_id":"77000000-0000-0000-0000-000000000001","aggregate_version":3,"business_code":null,"event_type":"AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED","schema_version":1,"occurred_at":"2026-07-27T13:30:00Z","payload":{"diagnostic":"ignored-raw-payload"}}\
                """;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value));
    }
}
