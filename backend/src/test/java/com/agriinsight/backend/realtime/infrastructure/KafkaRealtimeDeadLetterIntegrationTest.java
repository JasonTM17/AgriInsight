package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEventParser;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

class KafkaRealtimeDeadLetterIntegrationTest {

    @Test
    @Timeout(30)
    void retriesThenPublishesToTheSameDltPartitionBeforeCommittingTheSourceOffset() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RealtimeReadModelStore failingStore = event -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("simulated durable-store failure");
        };
        KafkaRealtimeOperationalEventConsumer listener = new KafkaRealtimeOperationalEventConsumer(
                new RealtimeOperationalEventParser(new JsonMapper()),
                new RealtimeEventIngestionService(failingStore, TransactionOperations.withoutTransaction()),
                KafkaRealtimeTestRecords.properties());

        try (KafkaRealtimeDltIntegrationSupport support = KafkaRealtimeDltIntegrationSupport.start()) {
            KafkaRealtimeDltIntegrationSupport.DltResult result =
                    support.recover(listener, KafkaRealtimeTestRecords.validSourceRecord());

            assertThat(attempts.get()).isEqualTo(3);
            assertThat(result.recovered().partition())
                    .isEqualTo(KafkaRealtimeDltIntegrationSupport.SOURCE_PARTITION);
            assertThat(result.recovered().key()).containsExactly(KafkaRealtimeTestRecords.key());
            assertThat(result.recovered().value()).containsExactly(KafkaRealtimeTestRecords.value());
            assertThat(result.sourceOffsetBeforeDltConfirmation()).isNull();
            assertThat(result.sourceOffsetAfterDltConfirmation().offset())
                    .isEqualTo(result.sourceRecordOffset() + 1);
        }
    }
}
