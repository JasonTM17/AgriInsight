package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Worker-only persistence boundary for fair, bounded cross-tenant condition scans. */
public interface RealtimeOperationalAlertScanStore {

    Optional<RealtimeOperationalAlertScanProgress> findProgress(RealtimeOperationalAlertPolicy policy);

    RealtimeOperationalAlertScanPage findPage(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Optional<RealtimeOperationalAlertScanCursor> cursor,
            int limit);

    List<RealtimeOperationalAlertRecoveryCandidate> findRecoveryCandidates(
            RealtimeOperationalAlertPolicy policy,
            Instant threshold,
            Instant staleBefore,
            int limit);

    void saveProgress(
            RealtimeOperationalAlertPolicy policy,
            RealtimeOperationalAlertScanProgress progress,
            Instant updatedAt);

    void clearProgress(RealtimeOperationalAlertPolicy policy);
}
