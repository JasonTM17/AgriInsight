package com.agriinsight.backend.integration.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID tenantId,
        UUID commandId,
        int eventOrdinal,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String payloadJson,
        OutboxStatus status,
        int attempts,
        int maxAttempts,
        Instant availableAt,
        Optional<Instant> leasedUntil,
        Optional<Instant> publishedAt,
        Optional<Instant> deadLetteredAt,
        Optional<String> leaseOwner,
        Optional<UUID> leaseToken,
        long leaseGeneration,
        Optional<String> lastError) {

    public OutboxEvent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(commandId, "commandId is required");
        if (eventOrdinal < 0) {
            throw new IllegalArgumentException("eventOrdinal must not be negative");
        }
        aggregateType = Objects.requireNonNull(aggregateType, "aggregateType is required");
        aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        eventType = Objects.requireNonNull(eventType, "eventType is required");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(payloadJson, "payloadJson is required");
        Objects.requireNonNull(status, "status is required");
        if (attempts < 0 || maxAttempts < 1 || attempts > maxAttempts) {
            throw new IllegalArgumentException("invalid outbox attempt bounds");
        }
        Objects.requireNonNull(availableAt, "availableAt is required");
        leasedUntil = Objects.requireNonNull(leasedUntil, "leasedUntil is required");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
        deadLetteredAt = Objects.requireNonNull(deadLetteredAt, "deadLetteredAt is required");
        leaseOwner = Objects.requireNonNull(leaseOwner, "leaseOwner is required");
        leaseToken = Objects.requireNonNull(leaseToken, "leaseToken is required");
        if (leaseGeneration < 0) {
            throw new IllegalArgumentException("leaseGeneration must not be negative");
        }
        lastError = Objects.requireNonNull(lastError, "lastError is required");
    }
}
