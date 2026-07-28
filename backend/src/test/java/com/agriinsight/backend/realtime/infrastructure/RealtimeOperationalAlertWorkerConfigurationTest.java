package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.SendResult;
import org.springframework.context.annotation.Profile;

class RealtimeOperationalAlertWorkerConfigurationTest {

    @Test
    void sendsObserverFailuresToTheDistinctTerminalTopic() {
        KafkaTemplate<byte[], byte[]> template = template();
        RealtimeWorkerProperties workerProperties = workerProperties(true);
        RealtimeAlertWorkerProperties alertProperties = alertProperties();
        ConsumerRecord<byte[], byte[]> source = new ConsumerRecord<>(
                workerProperties.deadLetterTopic(), 2, 7, new byte[] {1}, new byte[] {2});
        DeadLetterPublishingRecoverer recoverer =
                RealtimeOperationalAlertWorkerConfiguration.observerFailureRecoverer(
                        template, workerProperties, alertProperties);

        recoverer.accept(source, consumer(), new IllegalStateException("observer failure"));

        ArgumentCaptor<ProducerRecord<byte[], byte[]>> recovered = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(recovered.capture());
        assertThat(recovered.getValue().topic()).isEqualTo(alertProperties.observerFailureTopic());
        assertThat(recovered.getValue().topic()).isNotEqualTo(workerProperties.deadLetterTopic());
        assertThat(recovered.getValue().partition()).isEqualTo(2);
    }

    @Test
    void rejectsWorkerStartupWhenTheConsumerIsNotEnabled() {
        RealtimeWorkerRoleVerifier verifier = new RealtimeWorkerRoleVerifier(
                mock(JdbcTemplate.class), workerProperties(false), alertProperties());

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker requires the realtime consumer");
    }

    @Test
    void rejectsWorkerStartupWhenDatabaseRoleVerificationFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), any(Class.class), any(Object.class)))
                .thenReturn(false);
        RealtimeWorkerRoleVerifier verifier = new RealtimeWorkerRoleVerifier(
                jdbcTemplate, workerProperties(true), alertProperties());

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker database role verification failed");
    }

    @Test
    void evaluatorAndObserverAreLimitedToTheDedicatedWorkerProfile() {
        assertThat(RealtimeOperationalAlertSchedule.class.getAnnotation(Profile.class).value())
                .containsExactly("realtime-worker");
        assertThat(RealtimeDeadLetterAlertObserver.class.getAnnotation(Profile.class).value())
                .containsExactly("realtime-worker");
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<byte[], byte[]> template() {
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        return template;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<byte[], byte[]> consumer() {
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);
        when(consumer.partitionsFor("agriinsight.operational.v1.alert-observer-failure", Duration.ofSeconds(5)))
                .thenReturn(List.of(new PartitionInfo(
                        "agriinsight.operational.v1.alert-observer-failure", 2, null, null, null)));
        return consumer;
    }

    private static RealtimeWorkerProperties workerProperties(boolean consumerEnabled) {
        return new RealtimeWorkerProperties(
                false,
                consumerEnabled,
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                "agriinsight.operational.v1",
                "agriinsight.operational.v1.dlt",
                3,
                (short) 1,
                262_144);
    }

    private static RealtimeAlertWorkerProperties alertProperties() {
        return new RealtimeAlertWorkerProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                2,
                100,
                "agriinsight-alert-observer-v1",
                "agriinsight.operational.v1.alert-observer-failure",
                2,
                Duration.ofMillis(500));
    }
}
