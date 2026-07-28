package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.UUID;

/** Runtime-scoped port for immutable acknowledgement observations. */
public interface RealtimeOperationalAlertAcknowledgementStore {

    RealtimeAlertAcknowledgement acknowledge(
            UUID tenantId, UUID profileId, UUID alertId, Instant acknowledgedAt);
}
