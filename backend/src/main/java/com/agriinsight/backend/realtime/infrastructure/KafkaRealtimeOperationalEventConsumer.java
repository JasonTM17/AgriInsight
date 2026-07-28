package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEventParser;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes raw operational-event bytes and lets failures reach the configured retry/DLT handler. */
@Component
@ConditionalOnProperty(
        prefix = "agriinsight.realtime",
        name = "consumer-enabled",
        havingValue = "true")
public class KafkaRealtimeOperationalEventConsumer {

    private final RealtimeOperationalEventParser parser;
    private final RealtimeEventIngestionService ingestionService;
    private final RealtimeWorkerProperties properties;

    public KafkaRealtimeOperationalEventConsumer(
            RealtimeOperationalEventParser parser,
            RealtimeEventIngestionService ingestionService,
            RealtimeWorkerProperties properties) {
        this.parser = Objects.requireNonNull(parser, "parser is required");
        this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService is required");
        this.properties = Objects.requireNonNull(properties, "properties are required");
    }

    @KafkaListener(topics = "${agriinsight.realtime.topic}")
    public void consume(ConsumerRecord<byte[], byte[]> record) {
        RealtimeOperationalEvent event = parser.parse(record, properties.maxRecordBytes());
        ingestionService.ingest(event);
    }
}
