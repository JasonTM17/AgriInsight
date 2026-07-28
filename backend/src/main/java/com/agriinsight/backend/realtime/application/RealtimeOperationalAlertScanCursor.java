package com.agriinsight.backend.realtime.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Opaque, payload-free continuation state for a bounded worker scan. */
public record RealtimeOperationalAlertScanCursor(UUID tenantId, Instant orderedAt, UUID orderedId) {

    public RealtimeOperationalAlertScanCursor {
        boolean tenantCursor = tenantId != null;
        boolean orderedCursor = orderedAt != null || orderedId != null;
        if (tenantCursor == orderedCursor) {
            throw new IllegalArgumentException("scan cursor must use either tenant or ordered position");
        }
        if (orderedCursor && (orderedAt == null || orderedId == null)) {
            throw new IllegalArgumentException("ordered scan cursor requires timestamp and id");
        }
    }

    public static RealtimeOperationalAlertScanCursor tenant(UUID tenantId) {
        return new RealtimeOperationalAlertScanCursor(
                Objects.requireNonNull(tenantId, "tenantId is required"), null, null);
    }

    public static RealtimeOperationalAlertScanCursor ordered(Instant orderedAt, UUID orderedId) {
        return new RealtimeOperationalAlertScanCursor(
                null,
                Objects.requireNonNull(orderedAt, "orderedAt is required"),
                Objects.requireNonNull(orderedId, "orderedId is required"));
    }

    public boolean isTenantCursor() {
        return tenantId != null;
    }
}
