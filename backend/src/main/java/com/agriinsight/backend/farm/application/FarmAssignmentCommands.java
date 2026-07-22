package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Objects;
import java.util.UUID;

public final class FarmAssignmentCommands {

    private FarmAssignmentCommands() {
    }

    public record Grant(
            UUID userProfileId,
            UUID farmId,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Grant {
            Objects.requireNonNull(userProfileId, "userProfileId is required");
            Objects.requireNonNull(farmId, "farmId is required");
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
