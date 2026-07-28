package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.List;

/** Narrow metadata-only storage port used by the isolated realtime alert worker. */
public interface RealtimeOperationalAlertStore {

    boolean tryAcquirePolicyLock(RealtimeOperationalAlertPolicy policy);

    void acquirePolicyLock(RealtimeOperationalAlertPolicy policy);

    List<RealtimeOpenOperationalAlert> findOpenAlerts(
            RealtimeOperationalAlertPolicy policy, int limit);

    List<RealtimeOpenOperationalAlert> findStaleOpenAlerts(
            RealtimeOperationalAlertPolicy policy, Instant cycleStartedAt, int limit);

    void upsert(RealtimeOperationalAlertCondition condition, Instant observedAt);

    void recordClean(
            RealtimeOpenOperationalAlert alert,
            RealtimeAlertRecoveryTransition transition,
            Instant staleBefore,
            Instant evaluatedAt);
}
