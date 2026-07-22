package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class ActivityApplicationTestFixtures {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    static final UUID SEASON_ID = UUID.fromString("35000000-0000-0000-0000-000000000001");
    static final UUID ACTIVITY_ID = UUID.fromString("36000000-0000-0000-0000-000000000001");
    static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    static final ScopeContext LIST_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.ACTIVITY, Optional.empty());
    static final ScopeContext FARM_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));
    static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("ACTIVITY_CHANGE"), Optional.of("request-activity-04"));

    private ActivityApplicationTestFixtures() {
    }

    static ActivityRecord activity(long version, ActivityStatus status) {
        Optional<Instant> started = status == ActivityStatus.PLANNED
                ? Optional.empty() : Optional.of(Instant.parse("2027-01-01T01:00:00Z"));
        Optional<Instant> completed = status == ActivityStatus.COMPLETED
                ? Optional.of(Instant.parse("2027-01-01T02:00:00Z")) : Optional.empty();
        Optional<Instant> cancelled = status == ActivityStatus.CANCELLED
                ? Optional.of(Instant.parse("2027-01-01T02:00:00Z")) : Optional.empty();
        return new ActivityRecord(
                ACTIVITY_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, ActivityType.HARVEST,
                "ACTIVITY-A", "Activity A", Optional.of("Notes"),
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2027-01-01T03:00:00Z"),
                started, completed, cancelled, status, version);
    }

    static ActivityCommands.Create createCommand() {
        return new ActivityCommands.Create(
                FARM_ID, FIELD_ID, SEASON_ID, ActivityType.HARVEST,
                "ACTIVITY-A", "Activity A", Optional.of("Notes"),
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2027-01-01T03:00:00Z"), AUDIT);
    }

    static ActivityCommands.Update updateCommand(long version) {
        return new ActivityCommands.Update(
                Optional.empty(), Optional.empty(), Optional.of("Updated activity"),
                Optional.empty(), Optional.empty(), Optional.empty(), version, AUDIT);
    }

    private record TestPrincipal() implements TenantPrincipal {

        @Override
        public UUID profileId() {
            return PROFILE_ID;
        }

        @Override
        public UUID tenantId() {
            return TENANT_ID;
        }

        @Override
        public String getName() {
            return PROFILE_ID.toString();
        }
    }
}
