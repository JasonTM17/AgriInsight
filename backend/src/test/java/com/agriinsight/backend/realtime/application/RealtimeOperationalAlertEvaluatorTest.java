package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.realtime.infrastructure.RealtimeAlertWorkerProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.support.TransactionOperations;

class RealtimeOperationalAlertEvaluatorTest {

    private static final Instant NOW = Instant.parse("2027-09-01T12:00:00Z");
    private static final UUID TENANT_A =
            UUID.fromString("10000000-0000-0000-0000-000000000041");

    @ParameterizedTest
    @EnumSource(RealtimeOperationalAlertPolicy.class)
    void everySourcePageRunsBoundedRecoveryWhenAContinuationRemains(
            RealtimeOperationalAlertPolicy policy) {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalAlertScanCursor continuation = continuationFor(policy);
        RealtimeOperationalAlertCondition sourceCondition = sourceCondition(policy, 9);
        stores.pages(policy, page(List.of(candidate(sourceCondition)), Optional.of(continuation)));
        RealtimeOpenOperationalAlert healthyOldAlert = openAlert(policy, 1, NOW.minus(Duration.ofMinutes(5)), 1);
        stores.recoveryCandidates(policy, recovery(healthyOldAlert, Optional.empty()));

        evaluator(stores, properties(1)).evaluateAll();

        assertThat(stores.progressSaves(policy)).singleElement().satisfies(save -> {
            assertThat(save.progress().cursor()).isEqualTo(continuation);
            assertThat(save.progress().cycleStartedAt()).isEqualTo(NOW);
        });
        assertThat(stores.cleanUpdates()).singleElement().satisfies(update -> {
            assertThat(update.alert()).isEqualTo(healthyOldAlert);
            assertThat(update.transition().resolve()).isTrue();
            assertThat(update.staleBefore()).isEqualTo(NOW);
        });
        assertThat(stores.recoveryQueries(policy)).singleElement().satisfies(query -> {
            assertThat(query.staleBefore()).isEqualTo(NOW);
            assertThat(query.limit()).isEqualTo(2);
        });
        assertThat(stores.upserts()).containsExactly(new RecordingAlertStores.Upsert(sourceCondition, NOW));
    }

    @Test
    void receiptHeavySourcePageStillRunsBoundedRecoveryWhenNoConditionQualifies() {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG;
        RealtimeOperationalAlertScanCursor continuation = continuationFor(policy);
        stores.pages(policy, page(List.of(), Optional.of(continuation)));
        RealtimeOpenOperationalAlert healthyOldAlert = openAlert(policy, 1, NOW.minus(Duration.ofMinutes(5)), 1);
        stores.recoveryCandidates(policy, recovery(healthyOldAlert, Optional.empty()));

        evaluator(stores, properties(1)).evaluateAll();

        assertThat(stores.progressSaves(policy)).singleElement()
                .extracting(save -> save.progress().cursor())
                .isEqualTo(continuation);
        assertThat(stores.cleanUpdates()).singleElement()
                .extracting(RecordingAlertStores.CleanUpdate::alert)
                .isEqualTo(healthyOldAlert);
        assertThat(stores.recoveryQueries(policy)).singleElement()
                .extracting(RecordingAlertStores.RecoveryQuery::limit)
                .isEqualTo(2);
        assertThat(stores.upserts()).isEmpty();
    }

    @Test
    void currentRecoveryConditionRefreshesInsteadOfRecordingClean() {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        RealtimeOpenOperationalAlert oldAlert = openAlert(policy, 2, NOW.minus(Duration.ofMinutes(5)), 1);
        RealtimeOperationalAlertCondition currentCondition = backlogCondition(2);
        stores.recoveryCandidates(policy, recovery(oldAlert, Optional.of(currentCondition)));

        evaluator(stores, properties(1)).evaluateAll();

        assertThat(stores.upserts()).containsExactly(new RecordingAlertStores.Upsert(currentCondition, NOW));
        assertThat(stores.cleanUpdates()).isEmpty();
        assertThat(stores.recoveryQueries(policy)).singleElement().satisfies(query -> {
            assertThat(query.threshold()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
            assertThat(query.staleBefore()).isEqualTo(NOW);
            assertThat(query.limit()).isEqualTo(2);
        });
    }

    @Test
    void recoveryMutatesAtMostMaximumCandidatesAndRotatesTheOldestStaleAlerts() {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        RealtimeOpenOperationalAlert oldest = openAlert(policy, 1, null, 0);
        RealtimeOpenOperationalAlert next = openAlert(policy, 2, null, 0);
        RealtimeOpenOperationalAlert later = openAlert(policy, 3, null, 0);
        stores.recoveryCandidates(
                policy,
                recovery(oldest, Optional.empty()),
                recovery(next, Optional.empty()),
                recovery(later, Optional.empty()));
        RealtimeOperationalAlertEvaluator evaluator = evaluator(stores, properties(1));

        evaluator.evaluateAll();

        assertThat(stores.cleanUpdates()).extracting(RecordingAlertStores.CleanUpdate::alert)
                .containsExactly(oldest);
        assertThat(stores.recoveryQueries(policy)).singleElement()
                .extracting(RecordingAlertStores.RecoveryQuery::limit)
                .isEqualTo(2);

        evaluator.evaluateAll();

        assertThat(stores.cleanUpdates()).extracting(RecordingAlertStores.CleanUpdate::alert)
                .containsExactly(oldest, next);
        assertThat(stores.recoveryQueries(policy)).hasSize(2)
                .allSatisfy(query -> assertThat(query.limit()).isEqualTo(2));
    }

    @Test
    void recoveryDoesNotResolveBeforeTheHealthyDurationElapses() {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG;
        RealtimeOpenOperationalAlert alert = openAlert(policy, 1, NOW.minus(Duration.ofMinutes(4)), 1);
        stores.recoveryCandidates(policy, recovery(alert, Optional.empty()));

        evaluator(stores, properties(1)).evaluateAll();

        assertThat(stores.cleanUpdates()).singleElement().satisfies(update -> {
            assertThat(update.alert()).isEqualTo(alert);
            assertThat(update.transition().cleanScanCount()).isEqualTo(2);
            assertThat(update.transition().resolve()).isFalse();
        });
    }

    @Test
    void cursorResumeKeepsSourceConditionsAndHealthyRecoverySafe() {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalAlertPolicy policy = RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG;
        RealtimeOperationalAlertScanCursor continuation = RealtimeOperationalAlertScanCursor.ordered(
                NOW.minusSeconds(20), UUID.fromString("70000000-0000-0000-0000-000000000020"));
        RealtimeOperationalAlertCondition first = deliveryCondition(1, NOW.minus(Duration.ofMinutes(8)));
        RealtimeOperationalAlertCondition second = deliveryCondition(2, NOW.minus(Duration.ofMinutes(7)));
        stores.pages(
                policy,
                page(List.of(candidate(first)), Optional.of(continuation)),
                page(List.of(candidate(second)), Optional.empty()));
        RealtimeOpenOperationalAlert healthyOldAlert = openAlert(policy, 3, null, 0);
        stores.recoveryCandidates(policy, recovery(healthyOldAlert, Optional.empty()));
        RealtimeOperationalAlertEvaluator evaluator = evaluator(stores, properties(1));

        evaluator.evaluateAll();
        evaluator.evaluateAll();

        assertThat(stores.pageRequests(policy)).hasSize(2);
        assertThat(stores.pageRequests(policy).getFirst().cursor()).isEmpty();
        assertThat(stores.pageRequests(policy).get(1).cursor()).contains(continuation);
        assertThat(stores.upserts()).containsExactly(
                new RecordingAlertStores.Upsert(first, NOW),
                new RecordingAlertStores.Upsert(second, NOW));
        assertThat(stores.progressSaves(policy)).singleElement()
                .extracting(save -> save.progress().cursor())
                .isEqualTo(continuation);
        assertThat(stores.clearedPolicies()).contains(policy);
        assertThat(stores.cleanUpdates()).extracting(RecordingAlertStores.CleanUpdate::alert)
                .containsExactly(healthyOldAlert);
    }

    @Test
    void recordsADeduplicatedDltConditionInItsOwnTransaction() {
        RecordingAlertStores stores = new RecordingAlertStores();
        RealtimeOperationalEvent event = new RealtimeOperationalEvent(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                TENANT_A,
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                1,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                NOW.minusSeconds(20),
                "a".repeat(64),
                "agriinsight.operational.v1.dlt",
                1,
                3);

        evaluator(stores, properties(1)).observeDeadLetter(event);

        assertThat(stores.acquiredPolicies())
                .containsExactly(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD);
        assertThat(stores.upserts()).singleElement().satisfies(upsert -> {
            assertThat(upsert.condition().policy())
                    .isEqualTo(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD);
            assertThat(upsert.condition().tenantId()).isEqualTo(event.tenantId());
            assertThat(upsert.condition().sourceEventId()).isEqualTo(event.eventId());
            assertThat(upsert.observedAt()).isEqualTo(NOW);
        });
    }

    private static RealtimeOperationalAlertEvaluator evaluator(
            RecordingAlertStores stores, RealtimeAlertWorkerProperties properties) {
        TransactionOperations transaction = new ImmediateTransactions();
        return new RealtimeOperationalAlertEvaluator(
                stores,
                stores,
                transaction,
                transaction,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties,
                new SimpleMeterRegistry());
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
                Duration.ofSeconds(20),
                "agriinsight-alert-observer-v1",
                "agriinsight.operational.v1.alert-observer-failure",
                2,
                Duration.ofMillis(500));
    }

    private static RealtimeOperationalAlertCondition backlogCondition(int sequence) {
        return new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                tenant(sequence),
                null,
                NOW.minus(Duration.ofMinutes(10)));
    }

    private static RealtimeOperationalAlertCondition deliveryCondition(int sequence, Instant sourceOccurredAt) {
        return new RealtimeOperationalAlertCondition(
                RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG,
                tenant(sequence),
                UUID.fromString("70000000-0000-0000-0000-00000000000" + sequence),
                sourceOccurredAt);
    }

    private static RealtimeOperationalAlertCondition sourceCondition(
            RealtimeOperationalAlertPolicy policy, int sequence) {
        return switch (policy) {
            case OUTBOX_PUBLISH_BACKLOG -> backlogCondition(sequence);
            case REALTIME_DELIVERY_LAG -> deliveryCondition(sequence, NOW.minus(Duration.ofMinutes(10)));
            case REALTIME_DLT_RECORD -> new RealtimeOperationalAlertCondition(
                    policy,
                    tenant(sequence),
                    UUID.fromString("70000000-0000-0000-0000-00000000020" + sequence),
                    NOW.minus(Duration.ofMinutes(10)));
        };
    }

    private static RealtimeOpenOperationalAlert openAlert(
            RealtimeOperationalAlertPolicy policy, int sequence, Instant cleanSince, int cleanScanCount) {
        UUID tenantId = tenant(sequence);
        UUID sourceEventId = policy == RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG
                ? null
                : UUID.fromString("70000000-0000-0000-0000-00000000010" + sequence);
        return new RealtimeOpenOperationalAlert(
                UUID.fromString("80000000-0000-0000-0000-00000000000" + sequence),
                policy.dedupeKey(tenantId, sourceEventId),
                cleanSince,
                cleanScanCount);
    }

    private static UUID tenant(int sequence) {
        return UUID.fromString("10000000-0000-0000-0000-00000000004" + sequence);
    }

    private static RealtimeOperationalAlertScanCursor continuationFor(
            RealtimeOperationalAlertPolicy policy) {
        return policy == RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG
                ? RealtimeOperationalAlertScanCursor.tenant(tenant(9))
                : RealtimeOperationalAlertScanCursor.ordered(
                        NOW.minusSeconds(30), UUID.fromString("70000000-0000-0000-0000-000000000010"));
    }

    private static RealtimeOperationalAlertCandidate candidate(RealtimeOperationalAlertCondition condition) {
        return new RealtimeOperationalAlertCandidate(
                condition,
                condition.policy() == RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG
                        ? RealtimeOperationalAlertScanCursor.tenant(condition.tenantId())
                        : RealtimeOperationalAlertScanCursor.ordered(
                                condition.sourceOccurredAt(), condition.sourceEventId()));
    }

    private static RealtimeOperationalAlertRecoveryCandidate recovery(
            RealtimeOpenOperationalAlert alert,
            Optional<RealtimeOperationalAlertCondition> currentCondition) {
        return new RealtimeOperationalAlertRecoveryCandidate(alert, currentCondition);
    }

    private static RealtimeOperationalAlertScanPage page(
            List<RealtimeOperationalAlertCandidate> candidates,
            Optional<RealtimeOperationalAlertScanCursor> continuation) {
        return new RealtimeOperationalAlertScanPage(candidates, continuation, continuation.isPresent());
    }
}
