package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvidence;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RealtimeOperationalAlertResponse(
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                allowableValues = "realtime_operational")
        String source,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        UUID id,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "OUTBOX_PUBLISH_BACKLOG",
                    "REALTIME_DELIVERY_LAG",
                    "REALTIME_DLT_RECORD"
                })
        String policy,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                allowableValues = {"WARNING", "CRITICAL"})
        String severity,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                allowableValues = "OPEN")
        String state,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Evidence evidence,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant openedAt,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant sourceOccurredAt,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant lastObservedAt,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant lastEvaluatedAt,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                minimum = "0")
        long ageSeconds,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        boolean acknowledged,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                nullable = true)
        Instant acknowledgedAt) {

    public RealtimeOperationalAlertResponse {
        if (acknowledged != (acknowledgedAt != null)) {
            throw new IllegalArgumentException("Acknowledgement state and time do not match");
        }
    }

    static RealtimeOperationalAlertResponse from(RealtimeOperationalAlertView view) {
        Objects.requireNonNull(view, "view is required");
        return new RealtimeOperationalAlertResponse(
                RealtimeOperationalAlertView.SOURCE,
                view.id(),
                view.policy().name(),
                view.severity().name(),
                view.state(),
                Evidence.from(view.evidence()),
                view.openedAt(),
                view.sourceOccurredAt(),
                view.lastObservedAt(),
                view.lastEvaluatedAt(),
                view.ageSeconds(),
                view.acknowledged(),
                view.acknowledgedAt().orElse(null));
    }

    public record Evidence(
            @io.swagger.v3.oas.annotations.media.Schema(
                    requiredMode =
                            io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                    allowableValues = {"TENANT_BACKLOG", "OPERATIONAL_EVENT"})
            String type,
            @io.swagger.v3.oas.annotations.media.Schema(
                    requiredMode =
                            io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                    nullable = true)
            UUID id) {

        public Evidence {
            Objects.requireNonNull(type, "type is required");
            boolean tenantBacklog = RealtimeOperationalAlertEvidence.Type.TENANT_BACKLOG
                    .name()
                    .equals(type);
            if (tenantBacklog == (id != null)) {
                throw new IllegalArgumentException("Evidence type and identifier do not match");
            }
        }

        static Evidence from(RealtimeOperationalAlertEvidence evidence) {
            return new Evidence(evidence.type().name(), evidence.id().orElse(null));
        }
    }
}
