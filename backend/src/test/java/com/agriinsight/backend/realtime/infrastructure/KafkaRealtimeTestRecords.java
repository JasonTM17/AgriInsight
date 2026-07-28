package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;

final class KafkaRealtimeTestRecords {

    static final UUID EVENT_ID = UUID.fromString("77000000-0000-0000-0000-000000000020");
    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    static final UUID AGGREGATE_ID = UUID.fromString("77000000-0000-0000-0000-000000000001");
    static final String EVENT_TYPE = "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED";

    private KafkaRealtimeTestRecords() {}

    static ProducerRecord<byte[], byte[]> validSourceRecord() {
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(
                KafkaRealtimeDltIntegrationSupport.SOURCE_TOPIC,
                KafkaRealtimeDltIntegrationSupport.SOURCE_PARTITION,
                key(),
                value());
        record.headers().add("agriinsight-event-id", EVENT_ID.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("agriinsight-tenant-id", TENANT_ID.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("agriinsight-event-type", EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add("agriinsight-schema-version", "1".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    static byte[] key() {
        return (TENANT_ID + ":FARM:" + AGGREGATE_ID).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] value() {
        return ("{\"event_id\":\"" + EVENT_ID
                        + "\",\"tenant_id\":\"" + TENANT_ID
                        + "\",\"command_id\":\"77000000-0000-0000-0000-000000000010\""
                        + ",\"event_ordinal\":0,\"aggregate\":\"FARM\",\"aggregate_id\":\""
                        + AGGREGATE_ID
                        + "\",\"aggregate_version\":3,\"business_code\":null,\"event_type\":\""
                        + EVENT_TYPE
                        + "\",\"schema_version\":1,\"occurred_at\":\"2026-07-27T13:30:00Z\""
                        + ",\"payload\":{\"diagnostic\":\"DLT evidence only\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    static RealtimeWorkerProperties properties() {
        return new RealtimeWorkerProperties(
                false,
                true,
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                KafkaRealtimeDltIntegrationSupport.SOURCE_TOPIC,
                KafkaRealtimeDltIntegrationSupport.DEAD_LETTER_TOPIC,
                3,
                (short) 1,
                262_144);
    }
}
