package com.agriinsight.backend.realtime.application;

import com.agriinsight.backend.realtime.infrastructure.RealtimeAlertWorkerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/** Evaluates bounded transport-health conditions and applies recovery hysteresis. */
public class RealtimeOperationalAlertEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeOperationalAlertEvaluator.class);
    private static final String SCAN_SATURATION_METRIC = "agriinsight.realtime.alerts.scan.saturated";
    private static final String UNVERIFIED_DLT_METRIC = "agriinsight.realtime.alerts.dlt.unverified";

    private final RealtimeOperationalAlertStore store;
    private final RealtimeOperationalAlertScanStore scanStore;
    private final RealtimeOperationalEventSourceStore sourceStore;
    private final TransactionOperations evaluationTransaction;
    private final TransactionOperations deadLetterTransaction;
    private final Clock clock;
    private final RealtimeAlertWorkerProperties properties;
    private final MeterRegistry meterRegistry;
    private final Counter unverifiedDeadLetterCounter;
    private final Set<String> activeSaturations = ConcurrentHashMap.newKeySet();

    public RealtimeOperationalAlertEvaluator(
            RealtimeOperationalAlertStore store,
            RealtimeOperationalAlertScanStore scanStore,
            RealtimeOperationalEventSourceStore sourceStore,
            TransactionOperations evaluationTransaction,
            TransactionOperations deadLetterTransaction,
            Clock clock,
            RealtimeAlertWorkerProperties properties,
            MeterRegistry meterRegistry) {
        this.store = Objects.requireNonNull(store, "store is required");
        this.scanStore = Objects.requireNonNull(scanStore, "scanStore is required");
        this.sourceStore = Objects.requireNonNull(sourceStore, "sourceStore is required");
        this.evaluationTransaction = Objects.requireNonNull(
                evaluationTransaction, "evaluationTransaction is required");
        this.deadLetterTransaction = Objects.requireNonNull(
                deadLetterTransaction, "deadLetterTransaction is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry is required");
        this.unverifiedDeadLetterCounter = Counter.builder(UNVERIFIED_DLT_METRIC)
                .description("Schema-valid DLT records with no matching undelivered outbox source")
                .register(this.meterRegistry);
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
        deadLetterTransaction.executeWithoutResult(status -> {
            store.acquirePolicyLock(RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD);
            Optional<Instant> sourceOccurredAt = Objects.requireNonNull(
                    sourceStore.findOccurredAt(required.tenantId(), required.eventId()),
                    "source lookup result is required");
            if (sourceOccurredAt.isEmpty()) {
                unverifiedDeadLetterCounter.increment();
                LOGGER.warn("realtime_alert_dlt_unverified");
                return;
            }
            store.upsert(
                    new RealtimeOperationalAlertCondition(
                            RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD,
                            required.tenantId(),
                            required.eventId(),
                            sourceOccurredAt.orElseThrow()),
                    observedAt);
        });
    }

    private void evaluate(RealtimeOperationalAlertPolicy policy, Instant evaluatedAt) {
        evaluationTransaction.executeWithoutResult(status -> evaluateInTransaction(policy, evaluatedAt));
    }

    private void evaluateInTransaction(RealtimeOperationalAlertPolicy policy, Instant evaluatedAt) {
        if (!store.tryAcquirePolicyLock(policy)) {
            return;
        }
        int boundedLimit = properties.maximumCandidates() + 1;
        Optional<RealtimeOperationalAlertScanProgress> progress = scanStore.findProgress(policy);
        Optional<RealtimeOperationalAlertScanCursor> cursor = progress.map(
                RealtimeOperationalAlertScanProgress::cursor);
        Instant cycleStartedAt = progress.map(RealtimeOperationalAlertScanProgress::cycleStartedAt)
                .orElse(evaluatedAt);
        Instant conditionThreshold = threshold(policy, evaluatedAt);
        RealtimeOperationalAlertScanPage page = scanStore.findPage(
                policy, conditionThreshold, cursor, boundedLimit);
        for (RealtimeOperationalAlertCandidate candidate : page.candidates()) {
            RealtimeOperationalAlertCondition condition = candidate.condition();
            store.upsert(condition, evaluatedAt);
        }
        if (page.hasMore()) {
            scanStore.saveProgress(
                    policy,
                    new RealtimeOperationalAlertScanProgress(
                            page.continuationCursor().orElseThrow(), cycleStartedAt),
                    evaluatedAt);
            recordSaturation(policy, "conditions");
            recoverStaleAlerts(policy, conditionThreshold, evaluatedAt, boundedLimit);
            return;
        }
        clearSaturation(policy, "conditions");
        scanStore.clearProgress(policy);
        recoverStaleAlerts(policy, conditionThreshold, evaluatedAt, boundedLimit);
    }

    private void recoverStaleAlerts(
            RealtimeOperationalAlertPolicy policy,
            Instant conditionThreshold,
            Instant evaluatedAt,
            int boundedLimit) {
        List<RealtimeOperationalAlertRecoveryCandidate> recoveryCandidates =
                scanStore.findRecoveryCandidates(
                        policy, conditionThreshold, evaluatedAt, boundedLimit);
        if (recoveryCandidates.size() == boundedLimit) {
            recordSaturation(policy, "recovery");
        } else {
            clearSaturation(policy, "recovery");
        }
        List<RealtimeOperationalAlertRecoveryCandidate> boundedCandidates =
                recoveryCandidates.size() == boundedLimit
                        ? recoveryCandidates.subList(0, properties.maximumCandidates())
                        : recoveryCandidates;
        for (RealtimeOperationalAlertRecoveryCandidate candidate : boundedCandidates) {
            if (candidate.currentCondition().isPresent()) {
                store.upsert(candidate.currentCondition().orElseThrow(), evaluatedAt);
                continue;
            }
            RealtimeOpenOperationalAlert alert = candidate.alert();
            store.recordClean(
                    alert, recoveryTransition(alert, evaluatedAt), evaluatedAt, evaluatedAt);
        }
    }

    private void recordSaturation(RealtimeOperationalAlertPolicy policy, String stage) {
        Counter.builder(SCAN_SATURATION_METRIC)
                .description("Operational alert scans that reached the configured candidate bound")
                .tag("policy", policy.name())
                .tag("stage", stage)
                .register(meterRegistry)
                .increment();
        if (activeSaturations.add(saturationKey(policy, stage))) {
            LOGGER.warn(
                    "realtime_alert_scan_saturated policy={} stage={} maximumCandidates={}",
                    policy,
                    stage,
                    properties.maximumCandidates());
        }
    }

    private void clearSaturation(RealtimeOperationalAlertPolicy policy, String stage) {
        if (activeSaturations.remove(saturationKey(policy, stage))) {
            LOGGER.info("realtime_alert_scan_saturation_cleared policy={} stage={}", policy, stage);
        }
    }

    private static String saturationKey(RealtimeOperationalAlertPolicy policy, String stage) {
        return policy.name() + ':' + stage;
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
