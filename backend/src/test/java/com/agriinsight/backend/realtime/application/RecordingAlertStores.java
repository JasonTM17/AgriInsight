package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class RecordingAlertStores
        implements RealtimeOperationalAlertStore,
                RealtimeOperationalAlertScanStore,
                RealtimeOperationalEventSourceStore {

    private final Map<RealtimeOperationalAlertPolicy, List<RealtimeOperationalAlertScanPage>> pages =
            new EnumMap<>(RealtimeOperationalAlertPolicy.class);
    private final Map<RealtimeOperationalAlertPolicy, Integer> pageIndexes =
            new EnumMap<>(RealtimeOperationalAlertPolicy.class);
    private final Map<RealtimeOperationalAlertPolicy, List<RealtimeOperationalAlertRecoveryCandidate>>
            recoveryCandidates = new EnumMap<>(RealtimeOperationalAlertPolicy.class);
    private final Map<RealtimeOperationalAlertPolicy, RealtimeOperationalAlertScanProgress> progress =
            new EnumMap<>(RealtimeOperationalAlertPolicy.class);
    private final Map<SourceLookup, Instant> sourceOccurredAt = new HashMap<>();
    private final List<Upsert> upserts = new ArrayList<>();
    private final List<CleanUpdate> cleanUpdates = new ArrayList<>();
    private final List<ProgressSave> progressSaves = new ArrayList<>();
    private final List<ScanRequest> pageRequests = new ArrayList<>();
    private final List<RecoveryQuery> recoveryQueries = new ArrayList<>();
    private final List<RealtimeOperationalAlertPolicy> clearedPolicies = new ArrayList<>();
    private final List<RealtimeOperationalAlertPolicy> acquiredPolicies = new ArrayList<>();
    private final List<SourceLookup> sourceLookups = new ArrayList<>();
    private final Set<UUID> evaluatedAlerts = new HashSet<>();

    void pages(RealtimeOperationalAlertPolicy policy, RealtimeOperationalAlertScanPage... values) {
        pages.put(policy, List.of(values));
    }

    void recoveryCandidates(
            RealtimeOperationalAlertPolicy policy,
            RealtimeOperationalAlertRecoveryCandidate... values) {
        recoveryCandidates.put(policy, List.of(values));
    }

    void sourceOccurredAt(UUID tenantId, UUID eventId, Instant occurredAt) {
        sourceOccurredAt.put(
                new SourceLookup(tenantId, eventId),
                java.util.Objects.requireNonNull(occurredAt, "occurredAt is required"));
    }

    List<Upsert> upserts() {
        return upserts;
    }

    List<CleanUpdate> cleanUpdates() {
        return cleanUpdates;
    }

    List<ProgressSave> progressSaves(RealtimeOperationalAlertPolicy policy) {
        return progressSaves.stream().filter(save -> save.policy() == policy).toList();
    }

    List<ScanRequest> pageRequests(RealtimeOperationalAlertPolicy policy) {
        return pageRequests.stream().filter(request -> request.policy() == policy).toList();
    }

    List<RecoveryQuery> recoveryQueries(RealtimeOperationalAlertPolicy policy) {
        return recoveryQueries.stream().filter(query -> query.policy() == policy).toList();
    }

    List<RealtimeOperationalAlertPolicy> clearedPolicies() {
        return clearedPolicies;
    }

    List<RealtimeOperationalAlertPolicy> acquiredPolicies() {
        return acquiredPolicies;
    }

    List<SourceLookup> sourceLookups() {
        return sourceLookups;
    }

    @Override
    public boolean tryAcquirePolicyLock(RealtimeOperationalAlertPolicy policy) {
        return true;
    }

    @Override
    public void acquirePolicyLock(RealtimeOperationalAlertPolicy policy) {
        acquiredPolicies.add(policy);
    }

    @Override
    public List<RealtimeOpenOperationalAlert> findOpenAlerts(
            RealtimeOperationalAlertPolicy policy, int limit) {
        return List.of();
    }

    @Override
    public List<RealtimeOpenOperationalAlert> findStaleOpenAlerts(
            RealtimeOperationalAlertPolicy policy, Instant cycleStartedAt, int limit) {
        return List.of();
    }

    @Override
    public void upsert(RealtimeOperationalAlertCondition condition, Instant observedAt) {
        upserts.add(new Upsert(condition, observedAt));
    }

    @Override
    public void recordClean(
            RealtimeOpenOperationalAlert alert,
            RealtimeAlertRecoveryTransition transition,
            Instant staleBefore,
            Instant evaluatedAt) {
        evaluatedAlerts.add(alert.id());
        cleanUpdates.add(new CleanUpdate(alert, transition, staleBefore, evaluatedAt));
    }

    @Override
    public Optional<Instant> findOccurredAt(UUID tenantId, UUID eventId) {
        SourceLookup lookup = new SourceLookup(tenantId, eventId);
        sourceLookups.add(lookup);
        return Optional.ofNullable(sourceOccurredAt.get(lookup));
    }

    @Override
    public Optional<RealtimeOperationalAlertScanProgress> findProgress(
            RealtimeOperationalAlertPolicy policy) {
        return Optional.ofNullable(progress.get(policy));
    }

    @Override
    public RealtimeOperationalAlertScanPage findPage(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Optional<RealtimeOperationalAlertScanCursor> cursor,
            int limit) {
        pageRequests.add(new ScanRequest(policy, threshold, cursor, limit));
        int index = pageIndexes.merge(policy, 1, Integer::sum) - 1;
        return pages.getOrDefault(policy, List.of()).stream()
                .skip(index)
                .findFirst()
                .orElseGet(() -> new RealtimeOperationalAlertScanPage(List.of(), Optional.empty(), false));
    }

    @Override
    public List<RealtimeOperationalAlertRecoveryCandidate> findRecoveryCandidates(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Instant staleBefore,
            int limit) {
        recoveryQueries.add(new RecoveryQuery(policy, threshold, staleBefore, limit));
        return recoveryCandidates.getOrDefault(policy, List.of()).stream()
                .filter(candidate -> !evaluatedAlerts.contains(candidate.alert().id()))
                .limit(limit)
                .toList();
    }

    @Override
    public void saveProgress(
            RealtimeOperationalAlertPolicy policy,
            RealtimeOperationalAlertScanProgress savedProgress,
            Instant updatedAt) {
        progress.put(policy, savedProgress);
        progressSaves.add(new ProgressSave(policy, savedProgress, updatedAt));
    }

    @Override
    public void clearProgress(RealtimeOperationalAlertPolicy policy) {
        progress.remove(policy);
        clearedPolicies.add(policy);
    }

    record Upsert(RealtimeOperationalAlertCondition condition, Instant observedAt) {
    }

    record CleanUpdate(
            RealtimeOpenOperationalAlert alert,
            RealtimeAlertRecoveryTransition transition,
            Instant staleBefore,
            Instant evaluatedAt) {
    }

    record ProgressSave(
            RealtimeOperationalAlertPolicy policy,
            RealtimeOperationalAlertScanProgress progress,
            Instant updatedAt) {
    }

    record ScanRequest(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Optional<RealtimeOperationalAlertScanCursor> cursor,
            int limit) {
    }

    record RecoveryQuery(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Instant staleBefore,
            int limit) {
    }

    record SourceLookup(UUID tenantId, UUID eventId) {
    }
}
