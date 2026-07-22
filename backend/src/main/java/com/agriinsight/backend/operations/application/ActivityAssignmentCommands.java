package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Objects;
import java.util.UUID;

public final class ActivityAssignmentCommands {

    private ActivityAssignmentCommands() {
    }

    public record Grant(
            UUID employeeId,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Grant {
            Objects.requireNonNull(employeeId, "employeeId is required");
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Revoke(long expectedVersion, TenantAuditMetadata audit) {

        public Revoke {
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    private static void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
