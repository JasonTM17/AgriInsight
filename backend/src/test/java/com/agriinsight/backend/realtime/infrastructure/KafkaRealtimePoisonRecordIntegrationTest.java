package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEventParser;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

class KafkaRealtimePoisonRecordIntegrationTest {

    @Test
    @Timeout(30)
    void retriesAHeaderPoisonRecordThenPublishesRawBytesToTheSameDltPartition() throws Exception {
        AtomicInteger parseAttempts = new AtomicInteger();
        AtomicBoolean storeCalled = new AtomicBoolean();
        RealtimeOperationalEventParser parser = new RealtimeOperationalEventParser(new JsonMapper()) {
            @Override
            public RealtimeOperationalEvent parse(ConsumerRecord<byte[], byte[]> record, int maximumBytes) {
                parseAttempts.incrementAndGet();
                return super.parse(record, maximumBytes);
            }
        };
        RealtimeReadModelStore store = event -> {
            storeCalled.set(true);
            return RealtimeReadModelStore.ApplyResult.APPLIED;
        };
        KafkaRealtimeOperationalEventConsumer listener = new KafkaRealtimeOperationalEventConsumer(
                parser,
                new RealtimeEventIngestionService(store, TransactionOperations.withoutTransaction()),
                KafkaRealtimeTestRecords.properties());
        ProducerRecord<byte[], byte[]> poison = KafkaRealtimeTestRecords.validSourceRecord();
        poison.headers().remove("agriinsight-schema-version");
        poison.headers().add("agriinsight-schema-version", "2".getBytes(StandardCharsets.UTF_8));

        try (KafkaRealtimeDltIntegrationSupport support = KafkaRealtimeDltIntegrationSupport.start()) {
            KafkaRealtimeDltIntegrationSupport.DltResult result = support.recover(listener, poison);

            assertThat(parseAttempts.get()).isEqualTo(3);
            assertThat(storeCalled).isFalse();
            assertThat(result.recovered().partition())
                    .isEqualTo(KafkaRealtimeDltIntegrationSupport.SOURCE_PARTITION);
            assertThat(result.recovered().key()).containsExactly(poison.key());
            assertThat(result.recovered().value()).containsExactly(poison.value());
            assertThat(result.sourceOffsetBeforeDltConfirmation()).isNull();
            assertThat(result.sourceOffsetAfterDltConfirmation().offset())
                    .isEqualTo(result.sourceRecordOffset() + 1);
        }
    }
}
