package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.SendResult;

class RealtimeKafkaConsumerConfigurationTest {

    @Test
    void publishesRawBytesToTheConfiguredDeadLetterPartitionAndAcknowledgesOnlyAfterHandling() {
        KafkaTemplate<byte[], byte[]> template = templateWith(
                CompletableFuture.completedFuture(mock(SendResult.class)));
        RealtimeWorkerProperties properties = properties();
        ConsumerRecord<byte[], byte[]> source = new ConsumerRecord<>(
                "agriinsight.operational.v1",
                4,
                27,
                UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new byte[] {3, 2, 1});
        DeadLetterPublishingRecoverer recoverer =
                RealtimeKafkaConsumerConfiguration.deadLetterRecoverer(template, properties);
        Consumer<byte[], byte[]> consumer = consumerWithDeadLetterPartition();

        recoverer.accept(source, consumer, new IllegalStateException("invalid"));

        ArgumentCaptor<ProducerRecord<byte[], byte[]>> recovered = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(recovered.capture());
        assertThat(recovered.getValue().topic()).isEqualTo("agriinsight.operational.v1.dlt");
        assertThat(recovered.getValue().partition()).isEqualTo(4);
        assertThat(recovered.getValue().key()).containsExactly(source.key());
        assertThat(recovered.getValue().value()).containsExactly(source.value());
        DefaultErrorHandler errorHandler = RealtimeKafkaConsumerConfiguration.errorHandler(template, properties);
        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<byte[], byte[]> templateWith(
            CompletableFuture<SendResult<byte[], byte[]>> result) {
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(result);
        return template;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<byte[], byte[]> consumerWithDeadLetterPartition() {
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);
        when(consumer.partitionsFor("agriinsight.operational.v1.dlt", Duration.ofSeconds(5))).thenReturn(List.of(
                new PartitionInfo("agriinsight.operational.v1.dlt", 4, null, null, null)));
        return consumer;
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
