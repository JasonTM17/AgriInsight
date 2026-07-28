package com.agriinsight.backend.realtime.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Fixed metadata-only alert policies supported by the realtime worker. */
public enum RealtimeOperationalAlertPolicy {
    OUTBOX_PUBLISH_BACKLOG(RealtimeOperationalAlertSeverity.WARNING, false),
    REALTIME_DELIVERY_LAG(RealtimeOperationalAlertSeverity.CRITICAL, true),
    REALTIME_DLT_RECORD(RealtimeOperationalAlertSeverity.CRITICAL, true);

    private final RealtimeOperationalAlertSeverity severity;
    private final boolean sourceEventRequired;

    RealtimeOperationalAlertPolicy(
            RealtimeOperationalAlertSeverity severity, boolean sourceEventRequired) {
        this.severity = severity;
        this.sourceEventRequired = sourceEventRequired;
    }

    public RealtimeOperationalAlertSeverity severity() {
        return severity;
    }

    public String dedupeKey(UUID tenantId, UUID sourceEventId) {
        UUID requiredTenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        if (sourceEventRequired && sourceEventId == null) {
            throw new IllegalArgumentException(name() + " requires a sourceEventId");
        }
        String identity = name() + "\n" + requiredTenantId + "\n"
                + (sourceEventId == null ? "tenant" : sourceEventId);
        return sha256(identity);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
