package com.agriinsight.backend.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

class KafkaOperationalEventPublisherTest {

    @Test
    void sendsTheValidatedRecordAndWaitsForBrokerConfirmation() {
        KafkaTemplate<String, String> template = templateWith(
                CompletableFuture.completedFuture(mock(SendResult.class)));
        KafkaOperationalEventPublisher publisher =
                new KafkaOperationalEventPublisher(template, new JsonMapper(), properties());

        publisher.publish(event());

        ArgumentCaptor<ProducerRecord<String, String>> record =
                ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(template).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("agriinsight.operational.v1");
        assertThat(record.getValue().key())
                .isEqualTo("10000000-0000-0000-0000-000000000041:FARM:"
                        + "77000000-0000-0000-0000-000000000001");
        assertThat(new String(
                        record.getValue().headers().lastHeader("agriinsight-event-id").value(),
                        java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("77000000-0000-0000-0000-000000000020");
    }

    @Test
    void turnsUnconfirmedSendsIntoAWorkerFailure() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("timeout"));
        KafkaOperationalEventPublisher publisher =
                new KafkaOperationalEventPublisher(templateWith(failed), new JsonMapper(), properties());

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka did not confirm the operational event");
    }

    @Test
    void turnsSynchronousKafkaTemplateFailuresIntoAWorkerFailure() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("Kafka metadata is unavailable"));
        KafkaOperationalEventPublisher publisher =
                new KafkaOperationalEventPublisher(template, new JsonMapper(), properties());

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka did not confirm the operational event")
                .hasCauseInstanceOf(KafkaException.class);
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, String> templateWith(
            CompletableFuture<SendResult<String, String>> result) {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(result);
        return template;
    }

    private static RealtimeWorkerProperties properties() {
        return new RealtimeWorkerProperties(
                true,
                false,
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                "agriinsight.operational.v1",
                "agriinsight.operational.v1.dlt",
                6,
                (short) 1,
                262_144);
    }

    private static OutboxEvent event() {
        String payload = """
                {"event_id":"77000000-0000-0000-0000-000000000020","tenant_id":"10000000-0000-0000-0000-000000000041","command_id":"77000000-0000-0000-0000-000000000010","event_ordinal":0,"aggregate":"FARM","aggregate_id":"77000000-0000-0000-0000-000000000001","aggregate_version":3,"business_code":null,"event_type":"AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED","schema_version":1,"occurred_at":"2026-07-27T13:30:00Z","payload":{}}\
                """;
        return new OutboxEvent(
                UUID.fromString("77000000-0000-0000-0000-000000000020"),
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                UUID.fromString("77000000-0000-0000-0000-000000000010"),
                0,
                "FARM",
                UUID.fromString("77000000-0000-0000-0000-000000000001"),
                3,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                1,
                Instant.parse("2026-07-27T13:30:00Z"),
                payload,
                OutboxStatus.LEASED,
                1,
                5,
                Instant.parse("2026-07-27T13:30:00Z"),
                Optional.of(Instant.parse("2026-07-27T13:31:00Z")),
                Optional.empty(),
                Optional.empty(),
                Optional.of("realtime-worker-1"),
                Optional.of(UUID.randomUUID()),
                1,
                Optional.empty());
    }
}
