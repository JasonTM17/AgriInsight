package com.agriinsight.backend.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OperationalEventRecordTest {

    private static final UUID EVENT_ID =
            UUID.fromString("77000000-0000-0000-0000-000000000020");
    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID COMMAND_ID =
            UUID.fromString("77000000-0000-0000-0000-000000000010");
    private static final UUID AGGREGATE_ID =
            UUID.fromString("77000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-27T13:30:00Z");

    @Test
    void mapsTheExactV1EnvelopeToAStableAggregateRecord() {
        OperationalEventRecord record =
                OperationalEventRecord.from(event(validEnvelope()), new JsonMapper(), 262_144);

        assertThat(record.key()).isEqualTo(TENANT_ID + ":FARM:" + AGGREGATE_ID);
        assertThat(record.valueJson()).isEqualTo(validEnvelope());
        assertThat(record.headers())
                .containsExactly(
                        java.util.Map.entry("agriinsight-event-id", EVENT_ID.toString()),
                        java.util.Map.entry("agriinsight-tenant-id", TENANT_ID.toString()),
                        java.util.Map.entry("agriinsight-event-type", "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED"),
                        java.util.Map.entry("agriinsight-schema-version", "1"));
    }

    @Test
    void rejectsEnvelopeDriftAndOversizeRecords() {
        assertThatThrownBy(() -> OperationalEventRecord.from(
                        event(validEnvelope().replace(EVENT_ID.toString(), UUID.randomUUID().toString())),
                        new JsonMapper(),
                        262_144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event_id");
        assertThatThrownBy(() -> OperationalEventRecord.from(
                        event(validEnvelope().replace("\"payload\":{}", "\"unexpected\":{},\"payload\":{}")),
                        new JsonMapper(),
                        262_144))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fields");
        assertThatThrownBy(() ->
                        OperationalEventRecord.from(event(validEnvelope()), new JsonMapper(), 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
    }

    private static OutboxEvent event(String payload) {
        return new OutboxEvent(
                EVENT_ID,
                TENANT_ID,
                COMMAND_ID,
                0,
                "FARM",
                AGGREGATE_ID,
                3,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                1,
                OCCURRED_AT,
                payload,
                OutboxStatus.PENDING,
                0,
                5,
                OCCURRED_AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty());
    }

    private static String validEnvelope() {
        return """
                {"event_id":"77000000-0000-0000-0000-000000000020","tenant_id":"10000000-0000-0000-0000-000000000041","command_id":"77000000-0000-0000-0000-000000000010","event_ordinal":0,"aggregate":"FARM","aggregate_id":"77000000-0000-0000-0000-000000000001","aggregate_version":3,"business_code":null,"event_type":"AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED","schema_version":1,"occurred_at":"2026-07-27T13:30:00Z","payload":{}}\
                """;
    }
}
