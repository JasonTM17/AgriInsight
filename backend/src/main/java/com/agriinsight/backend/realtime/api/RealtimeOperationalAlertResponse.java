package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvidence;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RealtimeOperationalAlertResponse(
        String source,
        UUID id,
        String policy,
        String severity,
        String state,
        Evidence evidence,
        Instant openedAt,
        Instant sourceOccurredAt,
        Instant lastObservedAt,
        Instant lastEvaluatedAt,
        long ageSeconds,
        boolean acknowledged,
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

    public record Evidence(String type, UUID id) {

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
