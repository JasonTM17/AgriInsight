package com.agriinsight.backend.identity.application;

import java.util.Objects;
import java.util.Optional;

public final class TenantUserCommands {

    private TenantUserCommands() {
    }

    public record AuditMetadata(
            Optional<String> reasonCode,
            Optional<String> correlationId) {

        public AuditMetadata {
            reasonCode = Objects.requireNonNull(reasonCode, "reasonCode is required");
            correlationId = Objects.requireNonNull(correlationId, "correlationId is required");
        }
    }

    public record Create(
            String displayName,
            Optional<String> email,
            String issuer,
            String subject,
            AuditMetadata audit) {

        public Create {
            Objects.requireNonNull(displayName, "displayName is required");
            email = Objects.requireNonNull(email, "email is required");
            Objects.requireNonNull(issuer, "issuer is required");
            Objects.requireNonNull(subject, "subject is required");
            Objects.requireNonNull(audit, "audit is required");
        }

        @Override
        public String toString() {
            return "Create[profile=<redacted>, identity=<redacted>, audit=" + audit + "]";
        }
    }

    public record Lifecycle(
            long expectedVersion,
            AuditMetadata audit) {

        public Lifecycle {
            if (expectedVersion < 0) {
                throw new IllegalArgumentException("expectedVersion must not be negative");
            }
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record LinkIdentity(
            String issuer,
            String subject,
            AuditMetadata audit) {

        public LinkIdentity {
            Objects.requireNonNull(issuer, "issuer is required");
            Objects.requireNonNull(subject, "subject is required");
            Objects.requireNonNull(audit, "audit is required");
        }

        @Override
        public String toString() {
            return "LinkIdentity[identity=<redacted>, audit=" + audit + "]";
        }
    }
}
