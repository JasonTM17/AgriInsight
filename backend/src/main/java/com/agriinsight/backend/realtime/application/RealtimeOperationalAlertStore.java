package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.List;

/** Narrow metadata-only storage port used by the isolated realtime alert worker. */
public interface RealtimeOperationalAlertStore {

    boolean tryAcquirePolicyLock(RealtimeOperationalAlertPolicy policy);

    List<RealtimeOperationalAlertCondition> findConditions(
            RealtimeOperationalAlertPolicy policy, Instant threshold, int limit);

    List<RealtimeOpenOperationalAlert> findOpenAlerts(
            RealtimeOperationalAlertPolicy policy, int limit);

    void upsert(RealtimeOperationalAlertCondition condition, Instant observedAt);

    void recordClean(
            RealtimeOpenOperationalAlert alert,
            RealtimeAlertRecoveryTransition transition,
            Instant evaluatedAt);
}
