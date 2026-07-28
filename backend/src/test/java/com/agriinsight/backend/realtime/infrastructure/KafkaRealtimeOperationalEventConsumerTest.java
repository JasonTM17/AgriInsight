package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeEventValidationException;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEventParser;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore.ApplyResult;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class KafkaRealtimeOperationalEventConsumerTest {

    @Test
    void parsesRawBytesThenPersistsTheValidatedEvent() {
        RealtimeOperationalEventParser parser = mock(RealtimeOperationalEventParser.class);
        RealtimeEventIngestionService ingestionService = mock(RealtimeEventIngestionService.class);
        ConsumerRecord<byte[], byte[]> record = record();
        RealtimeOperationalEvent event = event();
        when(parser.parse(record, 262_144)).thenReturn(event);
        when(ingestionService.ingest(event)).thenReturn(ApplyResult.APPLIED);

        new KafkaRealtimeOperationalEventConsumer(parser, ingestionService, properties()).consume(record);

        verify(parser).parse(record, 262_144);
        verify(ingestionService).ingest(event);
    }

    @Test
    void propagatesParserFailuresToTheKafkaErrorHandler() {
        RealtimeOperationalEventParser parser = mock(RealtimeOperationalEventParser.class);
        RealtimeEventIngestionService ingestionService = mock(RealtimeEventIngestionService.class);
        ConsumerRecord<byte[], byte[]> record = record();
        when(parser.parse(record, 262_144)).thenThrow(new RealtimeEventValidationException("invalid"));

        assertThatThrownBy(() -> new KafkaRealtimeOperationalEventConsumer(
                        parser, ingestionService, properties()).consume(record))
                .isInstanceOf(RealtimeEventValidationException.class)
                .hasMessage("invalid");
        verify(ingestionService, never()).ingest(org.mockito.ArgumentMatchers.any());
    }

    private static ConsumerRecord<byte[], byte[]> record() {
        return new ConsumerRecord<>(
                "agriinsight.operational.v1", 2, 9, new byte[] {1}, new byte[] {2});
    }

    private static RealtimeOperationalEvent event() {
        return new RealtimeOperationalEvent(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                0,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                Instant.parse("2027-09-01T00:00:00Z"),
                "a".repeat(64),
                "agriinsight.operational.v1",
                2,
                9);
    }

    private static RealtimeWorkerProperties properties() {
        return new RealtimeWorkerProperties(
                false,
                true,
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
}
