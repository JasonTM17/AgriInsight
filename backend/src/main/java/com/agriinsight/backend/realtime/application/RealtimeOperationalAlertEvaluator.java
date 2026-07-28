package com.agriinsight.backend.realtime.application;

import com.agriinsight.backend.realtime.infrastructure.RealtimeAlertWorkerProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.support.TransactionOperations;

/** Evaluates bounded transport-health conditions and applies recovery hysteresis. */
public class RealtimeOperationalAlertEvaluator {

    private final RealtimeOperationalAlertStore store;
    private final TransactionOperations transaction;
    private final Clock clock;
    private final RealtimeAlertWorkerProperties properties;

    public RealtimeOperationalAlertEvaluator(
            RealtimeOperationalAlertStore store,
            TransactionOperations transaction,
            Clock clock,
            RealtimeAlertWorkerProperties properties) {
        this.store = Objects.requireNonNull(store, "store is required");
        this.transaction = Objects.requireNonNull(transaction, "transaction is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.properties = Objects.requireNonNull(properties, "properties is required");
    }

    public void evaluateAll() {
        Instant evaluatedAt = clock.instant();
        evaluate(RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, evaluatedAt);
        evaluate(RealtimeOperationalAlertPolicy.REALTIME_DELIVERY_LAG, evaluatedAt);
        evaluate(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, evaluatedAt);
    }

    public void observeDeadLetter(RealtimeOperationalEvent event) {
        RealtimeOperationalEvent required = Objects.requireNonNull(event, "event is required");
        Instant observedAt = clock.instant();
        transaction.executeWithoutResult(status -> store.upsert(
                new RealtimeOperationalAlertCondition(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                        required.tenantId(),
                        required.eventId(),
                        required.occurredAt()),
                observedAt));
    }

    private void evaluate(RealtimeOperationalAlertPolicy policy, Instant evaluatedAt) {
        transaction.executeWithoutResult(status -> evaluateInTransaction(policy, evaluatedAt));
    }

    private void evaluateInTransaction(RealtimeOperationalAlertPolicy policy, Instant evaluatedAt) {
        if (!store.tryAcquirePolicyLock(policy)) {
            return;
        }
        int boundedLimit = properties.maximumCandidates() + 1;
        List<RealtimeOperationalAlertCondition> conditions =
                store.findConditions(policy, threshold(policy, evaluatedAt), boundedLimit);
        boolean conditionsTruncated = conditions.size() == boundedLimit;
        List<RealtimeOperationalAlertCondition> boundedConditions = conditionsTruncated
                ? conditions.subList(0, properties.maximumCandidates())
                : conditions;
        Set<String> observedKeys = new HashSet<>();
        for (RealtimeOperationalAlertCondition condition : boundedConditions) {
            store.upsert(condition, evaluatedAt);
            observedKeys.add(condition.dedupeKey());
        }
        if (conditionsTruncated) {
            return;
        }

        List<RealtimeOpenOperationalAlert> openAlerts = store.findOpenAlerts(policy, boundedLimit);
        if (openAlerts.size() == boundedLimit) {
            return;
        }
        for (RealtimeOpenOperationalAlert alert : openAlerts) {
            if (!observedKeys.contains(alert.dedupeKey())) {
                store.recordClean(alert, recoveryTransition(alert, evaluatedAt), evaluatedAt);
            }
        }
    }

    private Instant threshold(RealtimeOperationalAlertPolicy policy, Instant evaluatedAt) {
        return switch (policy) {
            case OUTBOX_PUBLISH_BACKLOG -> evaluatedAt.minus(properties.publishBacklogThreshold());
            case REALTIME_DELIVERY_LAG -> evaluatedAt.minus(properties.deliveryLagThreshold());
            case REALTIME_DLT_RECORD -> evaluatedAt;
        };
    }

    private RealtimeAlertRecoveryTransition recoveryTransition(
            RealtimeOpenOperationalAlert alert, Instant evaluatedAt) {
        Instant cleanSince = alert.cleanSince() == null ? evaluatedAt : alert.cleanSince();
        int scans = alert.cleanScanCount() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : alert.cleanScanCount() + 1;
        boolean resolve = scans >= properties.requiredCleanScans()
                && !evaluatedAt.isBefore(cleanSince.plus(properties.healthyFor()));
        return new RealtimeAlertRecoveryTransition(cleanSince, scans, resolve);
    }
}
