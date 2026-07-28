package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.integration.application.OperationalEventRecord;
import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

final class RealtimeKafkaE2eFaultAssertions {

    private RealtimeKafkaE2eFaultAssertions() {
    }

    static void assertPausedKafkaBrokerRequeues(
            PostgresRealtimeE2eFixture database,
            KafkaContainer kafka,
            OutboxEvent event,
            String bootstrapServers,
            Runnable publishAvailable) throws Throwable {
        boolean paused = false;
        try {
            RealtimeKafkaE2eSupport.pauseKafka(kafka);
            paused = true;
            publishAvailable.run();
            RealtimeKafkaE2eSupport.await("outbox requeue after Kafka pause", Duration.ofSeconds(10), () -> {
                OutboxEvent observed = database.outbox(event.commandId());
                return observed.status() == OutboxStatus.PENDING && observed.attempts() == 1;
            });
            OutboxEvent requeued = database.outbox(event.commandId());
            assertThat(requeued.status()).isEqualTo(OutboxStatus.PENDING);
            assertThat(requeued.attempts()).isEqualTo(1);
            assertThat(requeued.publishedAt()).isEmpty();
        } finally {
            if (paused) {
                RealtimeKafkaE2eSupport.resumeKafka(kafka);
                RealtimeKafkaE2eSupport.awaitBrokerReady(bootstrapServers);
            }
        }
    }

    static void publishDuplicateRecord(String bootstrapServers, OutboxEvent source) throws Exception {
        OperationalEventRecord record = OperationalEventRecord.from(
                source, new JsonMapper(), RealtimeKafkaE2eSupport.MAX_RECORD_BYTES);
        try (var producer = RealtimeKafkaE2eSupport.rawProducer(bootstrapServers)) {
            producer.send(RealtimeKafkaE2eSupport.sourceRecord(record)).get();
        }
    }

    static void assertPoisonRecordReachesDlt(String bootstrapServers, OutboxEvent source)
            throws Throwable {
        OperationalEventRecord record = OperationalEventRecord.from(
                source, new JsonMapper(), RealtimeKafkaE2eSupport.MAX_RECORD_BYTES);
        ProducerRecord<byte[], byte[]> poison = RealtimeKafkaE2eSupport.poisonRecord(record);
        try (Consumer<byte[], byte[]> observer = RealtimeKafkaE2eSupport.deadLetterObserver(bootstrapServers);
                var producer = RealtimeKafkaE2eSupport.rawProducer(bootstrapServers)) {
            var dltPartition = new org.apache.kafka.common.TopicPartition(
                    RealtimeKafkaE2eSupport.DEAD_LETTER_TOPIC, 0);
            observer.assign(List.of(dltPartition));
            observer.seekToEnd(List.of(dltPartition));
            long sourceOffset = producer.send(poison).get().offset();
            ConsumerRecord<byte[], byte[]> recovered = RealtimeKafkaE2eSupport.awaitRecord(
                    observer, RealtimeKafkaE2eSupport.DEAD_LETTER_TOPIC, Duration.ofSeconds(20));
            assertThat(recovered.partition()).isZero();
            assertThat(recovered.key()).containsExactly(poison.key());
            assertThat(recovered.value()).containsExactly(poison.value());
            assertThat(new String(requiredHeader(recovered, KafkaHeaders.DLT_ORIGINAL_TOPIC), StandardCharsets.UTF_8))
                    .isEqualTo(RealtimeKafkaE2eSupport.TOPIC);
            assertThat(ByteBuffer.wrap(requiredHeader(recovered, KafkaHeaders.DLT_ORIGINAL_PARTITION)).getInt())
                    .isZero();
            assertThat(ByteBuffer.wrap(requiredHeader(recovered, KafkaHeaders.DLT_ORIGINAL_OFFSET)).getLong())
                    .isEqualTo(sourceOffset);
            RealtimeKafkaE2eSupport.awaitCommittedOffset(
                    bootstrapServers, RealtimeKafkaE2eSupport.TOPIC, sourceOffset + 1);
        }
    }

    private static byte[] requiredHeader(ConsumerRecord<byte[], byte[]> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        assertThat(header).as("DLT header %s", headerName).isNotNull();
        return header.value();
    }
}
