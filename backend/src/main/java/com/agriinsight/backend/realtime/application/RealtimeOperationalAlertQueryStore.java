package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Runtime-only read port for the fixed current-profile operational-alert feed. */
public interface RealtimeOperationalAlertQueryStore {

    List<RealtimeOperationalAlertView> findLatestOpen(
            UUID tenantId,
            UUID profileId,
            Instant generatedAt);

    Optional<RealtimeOperationalAlertView> findOpenById(
            UUID tenantId,
            UUID profileId,
            UUID alertId,
            Instant generatedAt);
}
