package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads the minimal source metadata needed to attribute a validated, undelivered DLT event. */
public interface RealtimeOperationalEventSourceStore {

    /** Returns a source timestamp only when no matching realtime receipt exists. */
    Optional<Instant> findOccurredAt(UUID tenantId, UUID eventId);
}
