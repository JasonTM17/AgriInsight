package com.agriinsight.backend.cost;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.cost.application.CostCorrectionRecord;
import com.agriinsight.backend.cost.application.OperatingCostRecord;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class CostHttpTestSupport {

    static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    static final UUID ACTOR_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    static final UUID SEASON_ID = UUID.fromString(
            "35000000-0000-0000-0000-000000000001");
    static final UUID ORIGINAL_ID = UUID.fromString(
            "67000000-0000-0000-0000-000000000001");
    static final UUID REVERSAL_ID = UUID.fromString(
            "67000000-0000-0000-0000-000000000002");
    static final UUID REPLACEMENT_ID = UUID.fromString(
            "67000000-0000-0000-0000-000000000003");
    static final String AUTHORIZATION = "Bearer cost-api-token";

    private CostHttpTestSupport() {
    }

    static void stubIdentity(
            JwtDecoder decoder,
            TenantPrincipalLoader principalLoader,
            Set<Permission> permissions) {
        when(decoder.decode("cost-api-token")).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID, TENANT_ID, "TENANT-A", Optional.of("Cost Admin"),
                Optional.empty(), Optional.of("mfa"), Set.of(Role.TENANT_ADMIN), permissions));
    }

    static OperatingCostRecord posting() {
        return record(
                REPLACEMENT_ID, CostEntryKind.POSTING, new BigDecimal("1150000"),
                Optional.empty(), UUID.fromString("67000000-0000-0000-0000-000000000010"));
    }

    static CostCorrectionRecord correction() {
        return new CostCorrectionRecord(
                record(
                        REVERSAL_ID, CostEntryKind.REVERSAL, new BigDecimal("1200000"),
                        Optional.of(ORIGINAL_ID),
                        UUID.fromString("67000000-0000-0000-0000-000000000011")),
                record(
                        REPLACEMENT_ID, CostEntryKind.POSTING, new BigDecimal("1150000"),
                        Optional.empty(),
                        UUID.fromString("67000000-0000-0000-0000-000000000011")));
    }

    static CommandExecutionResult.Completed<OperatingCostRecord> completedPosting() {
        OperatingCostRecord entry = posting();
        return new CommandExecutionResult.Completed<>(
                entry.commandReference(), false, 201,
                new CommandTarget("OPERATING_COST_ENTRY", entry.id(), entry.version()),
                Optional.of(entry));
    }

    static CommandExecutionResult.Completed<CostCorrectionRecord> completedCorrection() {
        CostCorrectionRecord correction = correction();
        return new CommandExecutionResult.Completed<>(
                correction.replacement().commandReference(), false, 201,
                new CommandTarget(
                        "OPERATING_COST_ENTRY",
                        correction.replacement().id(),
                        correction.replacement().version()),
                Optional.of(correction));
    }

    private static OperatingCostRecord record(
            UUID id,
            CostEntryKind kind,
            BigDecimal amount,
            Optional<UUID> reversalOf,
            UUID commandReference) {
        return new OperatingCostRecord(
                id, TENANT_ID, CostTarget.domain(CostTarget.Type.SEASON, SEASON_ID),
                CostCategory.LABOR, amount, kind,
                Instant.parse("2027-09-01T02:00:00Z"), Optional.of("Season labor"),
                Optional.of("PAYROLL-09"), reversalOf, commandReference, ACTOR_ID, 0);
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("cost-api-token")
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("cost-admin")
                .audience(java.util.List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
