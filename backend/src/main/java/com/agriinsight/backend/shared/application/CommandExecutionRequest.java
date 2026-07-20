package com.agriinsight.backend.shared.application;

import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CommandExecutionRequest(
        UUID tenantId,
        UUID principalId,
        IdempotencyKey idempotencyKey,
        CanonicalCommandHasher.Fingerprint fingerprint,
        Optional<String> correlationId) {

    public CommandExecutionRequest {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principalId, "principalId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(fingerprint, "fingerprint is required");
        correlationId = Objects.requireNonNull(correlationId, "correlationId is required")
                .map(String::strip)
                .map(value -> {
                    if (!value.matches("[A-Za-z0-9._:-]{1,128}")) {
                        throw new IllegalArgumentException("correlationId has an invalid format");
                    }
                    return value;
                });
    }
}
