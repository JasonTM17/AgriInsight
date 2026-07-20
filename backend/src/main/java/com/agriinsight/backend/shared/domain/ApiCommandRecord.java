package com.agriinsight.backend.shared.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record ApiCommandRecord(
        UUID commandId,
        UUID tenantId,
        UUID principalId,
        String httpMethod,
        String routeTemplate,
        String idempotencyKeyDigest,
        short canonicalSchemaVersion,
        String commandHash,
        State state,
        Optional<Integer> responseStatus,
        Optional<String> targetResourceType,
        Optional<UUID> targetResourceId,
        Optional<Long> targetVersion) {

    private static final Pattern HTTP_METHOD = Pattern.compile("[A-Z]{3,8}");
    private static final Pattern ROUTE_TEMPLATE = Pattern.compile("/api/v1/[A-Za-z0-9_{}./-]+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern RESOURCE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public ApiCommandRecord {
        Objects.requireNonNull(commandId, "commandId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principalId, "principalId is required");
        requirePattern(httpMethod, HTTP_METHOD, "httpMethod");
        requirePattern(routeTemplate, ROUTE_TEMPLATE, "routeTemplate");
        if (routeTemplate.length() > 240) {
            throw new IllegalArgumentException("routeTemplate must not exceed 240 characters");
        }
        requirePattern(idempotencyKeyDigest, SHA_256, "idempotencyKeyDigest");
        if (canonicalSchemaVersion <= 0) {
            throw new IllegalArgumentException("canonicalSchemaVersion must be positive");
        }
        requirePattern(commandHash, SHA_256, "commandHash");
        Objects.requireNonNull(state, "state is required");
        responseStatus = Objects.requireNonNull(responseStatus, "responseStatus is required");
        targetResourceType = Objects.requireNonNull(targetResourceType, "targetResourceType is required");
        targetResourceId = Objects.requireNonNull(targetResourceId, "targetResourceId is required");
        targetVersion = Objects.requireNonNull(targetVersion, "targetVersion is required");
        validateStateShape(state, responseStatus, targetResourceType, targetResourceId, targetVersion);
    }

    public static ApiCommandRecord inProgress(
            UUID commandId,
            UUID tenantId,
            UUID principalId,
            String httpMethod,
            String routeTemplate,
            String idempotencyKeyDigest,
            short canonicalSchemaVersion,
            String commandHash) {
        return new ApiCommandRecord(
                commandId,
                tenantId,
                principalId,
                httpMethod,
                routeTemplate,
                idempotencyKeyDigest,
                canonicalSchemaVersion,
                commandHash,
                State.IN_PROGRESS,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public ApiCommandRecord complete(
            int status,
            String resourceType,
            UUID resourceId,
            long resourceVersion) {
        if (state != State.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress command can be completed");
        }
        return new ApiCommandRecord(
                commandId,
                tenantId,
                principalId,
                httpMethod,
                routeTemplate,
                idempotencyKeyDigest,
                canonicalSchemaVersion,
                commandHash,
                State.COMPLETED,
                Optional.of(status),
                Optional.of(Objects.requireNonNull(resourceType, "resourceType is required")),
                Optional.of(Objects.requireNonNull(resourceId, "resourceId is required")),
                Optional.of(resourceVersion));
    }

    public boolean matches(short schemaVersion, String candidateHash) {
        Objects.requireNonNull(candidateHash, "candidateHash is required");
        return canonicalSchemaVersion == schemaVersion
                && MessageDigest.isEqual(
                        commandHash.getBytes(StandardCharsets.US_ASCII),
                        candidateHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static void validateStateShape(
            State state,
            Optional<Integer> responseStatus,
            Optional<String> targetResourceType,
            Optional<UUID> targetResourceId,
            Optional<Long> targetVersion) {
        boolean allEmpty = responseStatus.isEmpty()
                && targetResourceType.isEmpty()
                && targetResourceId.isEmpty()
                && targetVersion.isEmpty();
        if (state == State.IN_PROGRESS) {
            if (!allEmpty) {
                throw new IllegalArgumentException("In-progress command must not contain completion metadata");
            }
            return;
        }
        if (responseStatus.isEmpty()
                || targetResourceType.isEmpty()
                || targetResourceId.isEmpty()
                || targetVersion.isEmpty()) {
            throw new IllegalArgumentException("Completed command requires complete completion metadata");
        }
        int status = responseStatus.orElseThrow();
        if (status < 200 || status > 299) {
            throw new IllegalArgumentException("responseStatus must be successful");
        }
        requirePattern(targetResourceType.orElseThrow(), RESOURCE_TYPE, "targetResourceType");
        if (targetVersion.orElseThrow() < 0) {
            throw new IllegalArgumentException("targetVersion must not be negative");
        }
    }

    private static void requirePattern(String value, Pattern pattern, String fieldName) {
        String required = Objects.requireNonNull(value, fieldName + " is required");
        if (!pattern.matcher(required).matches()) {
            throw new IllegalArgumentException(fieldName + " has an invalid format");
        }
    }

    public enum State {
        IN_PROGRESS,
        COMPLETED
    }
}
