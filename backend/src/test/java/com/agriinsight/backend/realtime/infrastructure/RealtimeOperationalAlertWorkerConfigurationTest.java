package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvaluator;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertScanStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeOperationalAlertWorkerConfigurationTest {

    @Test
    void sendsObserverFailuresToTheDistinctTerminalTopic() {
        KafkaTemplate<byte[], byte[]> template = template();
        RealtimeWorkerProperties workerProperties = workerProperties(false, false);
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
    void declaresTheObservedDltAndFailureTopicsWithWorkerSizing() {
        RealtimeOperationalAlertWorkerConfiguration configuration =
                new RealtimeOperationalAlertWorkerConfiguration();
        RealtimeWorkerProperties workerProperties = workerProperties(false, false);
        RealtimeAlertWorkerProperties alertProperties = alertProperties();

        NewTopic observed = configuration.realtimeObservedDeadLetterTopic(workerProperties);
        NewTopic failures = configuration.realtimeAlertObserverFailureTopic(workerProperties, alertProperties);

        assertThat(observed.name()).isEqualTo(workerProperties.deadLetterTopic());
        assertThat(failures.name()).isEqualTo(alertProperties.observerFailureTopic());
        assertThat(failures.name()).isNotEqualTo(observed.name());
        assertThat(List.of(observed, failures)).allSatisfy(topic -> {
            assertThat(topic.numPartitions()).isEqualTo(workerProperties.partitions());
            assertThat(topic.replicationFactor()).isEqualTo(workerProperties.replicationFactor());
            assertThat(topic.configs()).containsEntry(
                    "max.message.bytes", Integer.toString(workerProperties.maxRecordBytes()));
        });
    }

    @Test
    void rejectsWorkerStartupWhenTheLegacyRealtimePipelineIsEnabled() {
        RealtimeWorkerRoleVerifier verifier = new RealtimeWorkerRoleVerifier(
                mock(JdbcTemplate.class),
                workerProperties(false, true),
                alertProperties(),
                kafkaProperties());

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker cannot enable the legacy realtime pipeline");
    }

    @Test
    void rejectsWorkerStartupWhenDatabaseRoleVerificationFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), any(Class.class), any(Object.class)))
                .thenReturn(false);
        RealtimeWorkerRoleVerifier verifier = new RealtimeWorkerRoleVerifier(
                jdbcTemplate,
                workerProperties(false, false),
                alertProperties(),
                kafkaProperties());

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker database role verification failed");
    }

    @Test
    void rejectsWorkerStartupWhenLegacySourceEvidenceIsMissingOrInvalid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("current_user = CAST"),
                        eq(Boolean.class),
                        eq("agriinsight_alert_worker")))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(contains("source_occurred_at IS NULL"), eq(Boolean.class)))
                .thenReturn(false);
        RealtimeWorkerRoleVerifier verifier = new RealtimeWorkerRoleVerifier(
                jdbcTemplate,
                workerProperties(false, false),
                alertProperties(),
                kafkaProperties());

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker source evidence backfill is incomplete");
        ArgumentCaptor<String> sourceEvidenceQuery = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sourceEvidenceQuery.capture(), eq(Boolean.class));
        assertThat(sourceEvidenceQuery.getValue()).contains(
                "source_occurred_at IS NULL",
                "policy_code = 'OUTBOX_PUBLISH_BACKLOG'",
                "source_event_id IS NOT NULL",
                "policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')",
                "source_event_id IS NULL");
    }

    @Test
    void rejectsObserverFailureTopicThatAliasesAPrimaryTopic() {
        RealtimeWorkerRoleVerifier verifier = new RealtimeWorkerRoleVerifier(
                mock(JdbcTemplate.class),
                workerProperties(false, false),
                alertProperties("agriinsight.operational.v1"),
                kafkaProperties());

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("observer failure topic must differ from the primary and observed DLT topics");
    }

    @Test
    void evaluatorUsesRepeatableReadForBoundedPagesAndReadCommittedForDlt() {
        CapturingTransactionManager transactionManager = new CapturingTransactionManager();
        RealtimeOperationalAlertEvaluator evaluator = new RealtimeOperationalAlertWorkerConfiguration()
                .realtimeOperationalAlertEvaluator(
                        mock(RealtimeOperationalAlertStore.class),
                        mock(RealtimeOperationalAlertScanStore.class),
                        transactionManager,
                        Clock.fixed(Instant.parse("2027-09-01T12:00:00Z"), ZoneOffset.UTC),
                        alertProperties(),
                        new SimpleMeterRegistry());

        evaluator.evaluateAll();
        evaluator.observeDeadLetter(new RealtimeOperationalEvent(
                java.util.UUID.fromString("70000000-0000-0000-0000-000000000001"),
                java.util.UUID.fromString("10000000-0000-0000-0000-000000000041"),
                "FARM",
                java.util.UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                Instant.parse("2027-09-01T11:59:00Z"),
                "a".repeat(64),
                "agriinsight.operational.v1.dlt",
                1,
                3));

        assertThat(transactionManager.definitions()).hasSize(4);
        assertThat(transactionManager.definitions().subList(0, 3)).allSatisfy(definition -> {
            assertThat(definition.getName()).isEqualTo("realtime-operational-alerts");
            assertThat(definition.getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThat(definition.getIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertThat(definition.getTimeout()).isEqualTo(20);
        });
        TransactionDefinition dlt = transactionManager.definitions().getLast();
        assertThat(dlt.getName()).isEqualTo("realtime-operational-alert-dlt");
        assertThat(dlt.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(dlt.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThat(dlt.getTimeout()).isEqualTo(20);
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

    private static RealtimeWorkerProperties workerProperties(
            boolean publisherEnabled, boolean consumerEnabled) {
        return new RealtimeWorkerProperties(
                publisherEnabled,
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
        return alertProperties("agriinsight.operational.v1.alert-observer-failure");
    }

    private static RealtimeAlertWorkerProperties alertProperties(String observerFailureTopic) {
        return new RealtimeAlertWorkerProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                2,
                100,
                Duration.ofSeconds(20),
                "agriinsight-alert-observer-v1",
                observerFailureTopic,
                2,
                Duration.ofMillis(500));
    }

    private static KafkaProperties kafkaProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.getConsumer().setGroupId("agriinsight-realtime-v1");
        return properties;
    }

    private static final class CapturingTransactionManager implements PlatformTransactionManager {

        private final List<TransactionDefinition> definitions = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            definitions.add(definition);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }

        List<TransactionDefinition> definitions() {
            return definitions;
        }
    }
}
