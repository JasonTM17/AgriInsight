package com.agriinsight.backend.integration.infrastructure;

import com.agriinsight.backend.integration.application.OperationalEventPublisher;
import com.agriinsight.backend.integration.application.OperationalEventRecord;
import com.agriinsight.backend.integration.domain.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

public class KafkaOperationalEventPublisher implements OperationalEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final RealtimeWorkerProperties properties;

    public KafkaOperationalEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            RealtimeWorkerProperties properties) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is required");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper is required");
        this.properties = Objects.requireNonNull(properties, "properties are required");
    }

    @Override
    public void publish(OutboxEvent event) {
        OperationalEventRecord record =
                OperationalEventRecord.from(event, jsonMapper, properties.maxRecordBytes());
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                properties.topic(),
                null,
                record.key(),
                record.valueJson(),
                headers(record.headers()));
        try {
            kafkaTemplate
                    .send(producerRecord)
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Kafka did not confirm the operational event", exception);
        }
    }

    private static RecordHeaders headers(Map<String, String> values) {
        RecordHeaders headers = new RecordHeaders();
        values.forEach((key, value) ->
                headers.add(key, value.getBytes(StandardCharsets.UTF_8)));
        return headers;
    }
}
