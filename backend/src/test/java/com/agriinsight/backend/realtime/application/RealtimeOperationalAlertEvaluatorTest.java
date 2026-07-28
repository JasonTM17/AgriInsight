package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.realtime.infrastructure.RealtimeAlertWorkerProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RealtimeOperationalAlertEvaluatorTest {

    private static final Instant NOW = Instant.parse("2027-09-01T12:00:00Z");

    @Test
    void doesNotResolveAlertsWhenTheConditionScanIsTruncated() {
        RecordingStore store = new RecordingStore();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        store.conditions.put(policy, List.of(condition(policy, 1), condition(policy, 2)));
        store.openAlerts.put(policy, List.of(openAlert(NOW.minus(Duration.ofHours(1)), 2)));

        evaluator(store, properties(1)).evaluateAll();

        assertThat(store.upserts).hasSize(1);
        assertThat(store.cleanUpdates).isEmpty();
    }

    @Test
    void resolvesOnlyAfterTheRequiredCleanScansAndHealthyDuration() {
        RecordingStore store = new RecordingStore();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        store.openAlerts.put(policy, List.of(openAlert(NOW.minus(Duration.ofMinutes(5)), 1)));

        evaluator(store, properties(10)).evaluateAll();

        assertThat(store.cleanUpdates).hasSize(1);
        assertThat(store.cleanUpdates.getFirst().transition().cleanScanCount()).isEqualTo(2);
        assertThat(store.cleanUpdates.getFirst().transition().resolve()).isTrue();
    }

    @Test
    void keepsAlertOpenWhenHealthyDurationHasNotElapsed() {
        RecordingStore store = new RecordingStore();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        store.openAlerts.put(policy, List.of(openAlert(NOW.minus(Duration.ofMinutes(4)), 1)));

        evaluator(store, properties(10)).evaluateAll();

        assertThat(store.cleanUpdates).singleElement()
                .extracting(update -> update.transition().resolve())
                .isEqualTo(false);
    }

    @Test
    void recordsADeduplicatedDltConditionInItsOwnTransaction() {
        RecordingStore store = new RecordingStore();
        RealtimeOperationalEvent event = new RealtimeOperationalEvent(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                NOW.minusSeconds(20),
                "a".repeat(64),
                "agriinsight.operational.v1.dlt",
                1,
                3);

        evaluator(store, properties(10)).observeDeadLetter(event);

        assertThat(store.upserts).singleElement().satisfies(condition -> {
            assertThat(condition.policy()).isEqualTo(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD);
            assertThat(condition.tenantId()).isEqualTo(event.tenantId());
            assertThat(condition.sourceEventId()).isEqualTo(event.eventId());
        });
    }

    @Test
    void appliesRecoveryHysteresisToDltAlertsAfterTheirReceiptIsObserved() {
        RecordingStore store = new RecordingStore();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD;
        String dedupeKey = policy.dedupeKey(
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                UUID.fromString("70000000-0000-0000-0000-000000000001"));
        store.openAlerts.put(policy, List.of(new RealtimeOpenOperationalAlert(
                UUID.randomUUID(),
                dedupeKey,
                NOW.minus(Duration.ofMinutes(5)),
                1)));

        evaluator(store, properties(10)).evaluateAll();

        assertThat(store.cleanUpdates).singleElement().satisfies(update -> {
            assertThat(update.alert().dedupeKey()).isEqualTo(dedupeKey);
            assertThat(update.transition().resolve()).isTrue();
        });
    }

    private static RealtimeOperationalAlertEvaluator evaluator(
            RecordingStore store, RealtimeAlertWorkerProperties properties) {
        return new RealtimeOperationalAlertEvaluator(
                store,
                new ImmediateTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties);
    }

    private static RealtimeAlertWorkerProperties properties(int maximumCandidates) {
        return new RealtimeAlertWorkerProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                2,
                maximumCandidates,
                "agriinsight-alert-observer-v1",
                "agriinsight.operational.v1.alert-observer-failure",
                2,
                Duration.ofMillis(500));
    }

    private static RealtimeOperationalAlertCondition condition(
            RealtimeOperationalAlertPolicy policy, int sequence) {
        return new RealtimeOperationalAlertCondition(
                policy,
                UUID.fromString("10000000-0000-0000-0000-00000000004" + sequence),
                null,
                NOW.minus(Duration.ofMinutes(10)));
    }

    private static RealtimeOpenOperationalAlert openAlert(Instant cleanSince, int scans) {
        return new RealtimeOpenOperationalAlert(
                UUID.randomUUID(),
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG.dedupeKey(
                        UUID.fromString("10000000-0000-0000-0000-000000000041"), null),
                cleanSince,
                scans);
    }

    private static final class ImmediateTransactions implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }

    private static final class RecordingStore implements RealtimeOperationalAlertStore {

        private final Map<RealtimeOperationalAlertPolicy, List<RealtimeOperationalAlertCondition>> conditions =
                new EnumMap<>(RealtimeOperationalAlertPolicy.class);
        private final Map<RealtimeOperationalAlertPolicy, List<RealtimeOpenOperationalAlert>> openAlerts =
                new EnumMap<>(RealtimeOperationalAlertPolicy.class);
        private final List<RealtimeOperationalAlertCondition> upserts = new ArrayList<>();
        private final List<CleanUpdate> cleanUpdates = new ArrayList<>();

        @Override
        public boolean tryAcquirePolicyLock(RealtimeOperationalAlertPolicy policy) {
            return true;
        }

        @Override
        public List<RealtimeOperationalAlertCondition> findConditions(
                RealtimeOperationalAlertPolicy policy, Instant threshold, int limit) {
            return conditions.getOrDefault(policy, List.of());
        }

        @Override
        public List<RealtimeOpenOperationalAlert> findOpenAlerts(
                RealtimeOperationalAlertPolicy policy, int limit) {
            return openAlerts.getOrDefault(policy, List.of());
        }

        @Override
        public void upsert(RealtimeOperationalAlertCondition condition, Instant observedAt) {
            upserts.add(condition);
        }

        @Override
        public void recordClean(
                RealtimeOpenOperationalAlert alert,
                RealtimeAlertRecoveryTransition transition,
                Instant evaluatedAt) {
            cleanUpdates.add(new CleanUpdate(alert, transition));
        }
    }

    private record CleanUpdate(
            RealtimeOpenOperationalAlert alert, RealtimeAlertRecoveryTransition transition) {
    }
}
