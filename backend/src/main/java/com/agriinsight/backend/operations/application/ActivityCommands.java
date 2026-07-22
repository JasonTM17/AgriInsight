package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ActivityCommands {

    private ActivityCommands() {
    }

    public record Create(
            UUID farmId,
            UUID fieldId,
            UUID seasonId,
            ActivityType activityType,
            String code,
            String title,
            Optional<String> description,
            Instant plannedStartAt,
            Instant dueAt,
            TenantAuditMetadata audit) {

        public Create {
            Objects.requireNonNull(farmId, "farmId is required");
            Objects.requireNonNull(fieldId, "fieldId is required");
            Objects.requireNonNull(seasonId, "seasonId is required");
            Objects.requireNonNull(activityType, "activityType is required");
            code = Activity.canonicalCode(code);
            title = Activity.canonicalTitle(title);
            description = Activity.optionalDescription(description);
            Activity.requireSchedule(plannedStartAt, dueAt);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<ActivityType> activityType,
            Optional<String> code,
            Optional<String> title,
            Optional<Optional<String>> description,
            Optional<Instant> plannedStartAt,
            Optional<Instant> dueAt,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            activityType = Objects.requireNonNull(activityType, "activityType is required");
            code = Objects.requireNonNull(code, "code is required").map(Activity::canonicalCode);
            title = Objects.requireNonNull(title, "title is required").map(Activity::canonicalTitle);
            description = Objects.requireNonNull(description, "description is required")
                    .map(Activity::optionalDescription);
            plannedStartAt = Objects.requireNonNull(plannedStartAt, "plannedStartAt is required");
            dueAt = Objects.requireNonNull(dueAt, "dueAt is required");
            if (activityType.isEmpty() && code.isEmpty() && title.isEmpty() && description.isEmpty()
                    && plannedStartAt.isEmpty() && dueAt.isEmpty()) {
                throw new IllegalArgumentException("at least one activity field must be provided");
            }
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Transition(
            ActivityStatus targetStatus,
            Instant effectiveAt,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Transition {
            Objects.requireNonNull(targetStatus, "targetStatus is required");
            Objects.requireNonNull(effectiveAt, "effectiveAt is required");
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
