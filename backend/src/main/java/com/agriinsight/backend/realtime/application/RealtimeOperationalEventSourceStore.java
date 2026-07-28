package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads the minimal source metadata needed to attribute a validated DLT event. */
public interface RealtimeOperationalEventSourceStore {

    Optional<Instant> findOccurredAt(UUID tenantId, UUID eventId);
}
