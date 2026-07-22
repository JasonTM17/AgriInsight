package com.agriinsight.backend.farm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.farm.application.FarmRecord;
import com.agriinsight.backend.farm.application.FarmAssignmentRecord;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class FarmHttpTestSupport {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    static final UUID TARGET_PROFILE_ID = UUID.fromString("21000000-0000-0000-0000-000000000002");
    static final UUID FARM_ASSIGNMENT_ID = UUID.fromString("38000000-0000-0000-0000-000000000001");
    static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000001");
    static final String TOKEN = "farm-api-token";
    static final String AUTHORIZATION = "Bearer " + TOKEN;

    private FarmHttpTestSupport() {
    }

    static void stubIdentity(
            JwtDecoder decoder,
            TenantPrincipalLoader principalLoader,
            Set<Permission> permissions) {
        when(decoder.decode(TOKEN)).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID,
                TENANT_ID,
                "TENANT-A",
                Optional.of("Farm Admin"),
                Optional.empty(),
                Optional.of("mfa"),
                Set.of(Role.TENANT_ADMIN),
                permissions));
    }

    static FarmRecord farm(long version) {
        return farm(version, true);
    }

    static FarmRecord farm(long version, boolean active) {
        return new FarmRecord(FARM_ID, TENANT_ID, "NORTH", "North Farm", active, version);
    }

    static CommandExecutionResult.Completed<FarmRecord> completed(
            int status,
            FarmRecord farm) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                false,
                status,
                new CommandTarget("FARM", farm.id(), farm.version()),
                Optional.of(farm));
    }

    static FarmAssignmentRecord assignment(long version, boolean active) {
        return new FarmAssignmentRecord(
                FARM_ASSIGNMENT_ID,
                TENANT_ID,
                TARGET_PROFILE_ID,
                FARM_ID,
                active ? Optional.empty() : Optional.of(Instant.EPOCH),
                version);
    }

    static CommandExecutionResult.Completed<FarmAssignmentRecord> completedAssignment(
            int status,
            FarmAssignmentRecord assignment) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                false,
                status,
                new CommandTarget("FARM_ASSIGNMENT", assignment.id(), assignment.version()),
                Optional.of(assignment));
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("provider-admin")
                .audience(java.util.List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
