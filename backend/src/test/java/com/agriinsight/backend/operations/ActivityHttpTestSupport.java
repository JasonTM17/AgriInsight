package com.agriinsight.backend.operations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.ActivityRecord;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class ActivityHttpTestSupport {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    static final UUID SEASON_ID = UUID.fromString("35000000-0000-0000-0000-000000000001");
    static final UUID ACTIVITY_ID = UUID.fromString("36000000-0000-0000-0000-000000000001");
    static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000006");
    static final String TOKEN = "activity-api-token";
    static final String AUTHORIZATION = "Bearer " + TOKEN;

    private ActivityHttpTestSupport() {
    }

    static void stubIdentity(
            JwtDecoder decoder,
            TenantPrincipalLoader principalLoader,
            Set<Permission> permissions) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("provider-admin")
                .audience(java.util.List.of("agriinsight-api"))
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(90))
                .claim("token_use", "access")
                .build());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID, TENANT_ID, "TENANT-A", Optional.of("Activity Admin"), Optional.empty(),
                Optional.of("mfa"), Set.of(Role.TENANT_ADMIN), permissions));
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

    static CommandExecutionResult.Completed<ActivityRecord> completed(
            int status,
            ActivityRecord activity) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID, false, status,
                new CommandTarget("ACTIVITY", activity.id(), activity.version()), Optional.of(activity));
    }
}
