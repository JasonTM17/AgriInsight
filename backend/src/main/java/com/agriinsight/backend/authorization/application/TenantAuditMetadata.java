package com.agriinsight.backend.authorization.application;

import java.util.Objects;
import java.util.Optional;

public record TenantAuditMetadata(
        Optional<String> reasonCode,
        Optional<String> correlationId) {

    public TenantAuditMetadata {
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode is required");
        correlationId = Objects.requireNonNull(correlationId, "correlationId is required");
    }
}
