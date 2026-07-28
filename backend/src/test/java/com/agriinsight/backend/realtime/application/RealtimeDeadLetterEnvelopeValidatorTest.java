package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RealtimeDeadLetterEnvelopeValidatorTest {

    @Test
    void acceptsAValidValueDespiteExtraDltHeadersAndAnUntrustedKey() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                "agriinsight.operational.v1.dlt",
                2,
                9,
                new byte[] {1, 2, 3},
                validValue());
        record.headers().add("kafka_dlt-exception-message", "untrusted".getBytes(StandardCharsets.UTF_8));
        record.headers().add("agriinsight-tenant-id", "wrong".getBytes(StandardCharsets.UTF_8));

        RealtimeOperationalEvent event = new RealtimeDeadLetterEnvelopeValidator(JsonMapper.builder().build())
                .parse(record, 262_144);

        assertThat(event.eventId().toString()).isEqualTo("77000000-0000-0000-0000-000000000020");
        assertThat(event.tenantId().toString()).isEqualTo("10000000-0000-0000-0000-000000000041");
        assertThat(event.topic()).isEqualTo("agriinsight.operational.v1.dlt");
        assertThat(event.checksum()).hasSize(64);
    }

    @Test
    void rejectsMalformedValuesWithoutDerivingTenantScopeFromHeaders() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                "agriinsight.operational.v1.dlt",
                0,
                0,
                null,
                "not-json".getBytes(StandardCharsets.UTF_8));
        record.headers().add(
                "agriinsight-tenant-id",
                "10000000-0000-0000-0000-000000000041".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new RealtimeDeadLetterEnvelopeValidator(JsonMapper.builder().build())
                        .parse(record, 262_144))
                .isInstanceOf(RealtimeEventValidationException.class);
    }

    private static byte[] validValue() {
        return ("{\"event_id\":\"77000000-0000-0000-0000-000000000020\""
                        + ",\"tenant_id\":\"10000000-0000-0000-0000-000000000041\""
                        + ",\"command_id\":\"77000000-0000-0000-0000-000000000010\""
                        + ",\"event_ordinal\":0,\"aggregate\":\"FARM\""
                        + ",\"aggregate_id\":\"77000000-0000-0000-0000-000000000001\""
                        + ",\"aggregate_version\":3,\"business_code\":null"
                        + ",\"event_type\":\"AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED\""
                        + ",\"schema_version\":1,\"occurred_at\":\"2026-07-27T13:30:00Z\""
                        + ",\"payload\":{\"diagnostic\":\"not retained\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
