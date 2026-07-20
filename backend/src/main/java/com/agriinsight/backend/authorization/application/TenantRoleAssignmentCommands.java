package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Role;
import java.util.Objects;

public final class TenantRoleAssignmentCommands {

    private TenantRoleAssignmentCommands() {
    }

    public record Grant(
            Role role,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Grant {
            Objects.requireNonNull(role, "role is required");
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Revoke(
            long expectedVersion,
            TenantAuditMetadata audit) {

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
