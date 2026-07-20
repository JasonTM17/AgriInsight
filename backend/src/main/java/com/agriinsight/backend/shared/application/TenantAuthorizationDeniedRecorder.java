package com.agriinsight.backend.shared.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Records a tenant-resolved authorization denial without exposing request credentials. */
@FunctionalInterface
public interface TenantAuthorizationDeniedRecorder {

    void record(Decision decision);

    record Decision(
            UUID tenantId,
            UUID principalId,
            String targetReference,
            String reasonCode,
            Optional<UUID> targetId,
            Optional<String> correlationId) {

        private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
        private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

        public Decision {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(principalId, "principalId is required");
            targetReference = requiredText(targetReference, "targetReference", 200);
            reasonCode = requiredText(reasonCode, "reasonCode", 80);
            if (!REASON_CODE.matcher(reasonCode).matches()) {
                throw new IllegalArgumentException("reasonCode has an invalid format");
            }
            targetId = Objects.requireNonNull(targetId, "targetId is required");
            correlationId = Objects.requireNonNull(correlationId, "correlationId is required")
                    .map(value -> requiredText(value, "correlationId", 128));
            correlationId.ifPresent(value -> {
                if (!CORRELATION_ID.matcher(value).matches()) {
                    throw new IllegalArgumentException("correlationId has an invalid format");
                }
            });
        }

        private static String requiredText(String value, String fieldName, int maxLength) {
            String normalized = Objects.requireNonNull(value, fieldName + " is required").strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            if (normalized.length() > maxLength) {
                throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
            }
            return normalized;
        }
    }
}
