package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import com.agriinsight.backend.cost.infrastructure.PostgresOperatingCostStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresOperatingCostStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000005");
    private static final UUID FIELD_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000003");
    private static final UUID SEASON_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000006");
    private static final UUID ORIGINAL_ID = UUID.fromString(
            "65000000-0000-0000-0000-000000000001");
    private static final UUID ORIGINAL_COMMAND = UUID.fromString(
            "65000000-0000-0000-0000-000000000010");
    private static final UUID CORRECTION_COMMAND = UUID.fromString(
            "65000000-0000-0000-0000-000000000011");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                    VALUES ('65000000-0000-0000-0000-000000000020',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000005', 'TENANT_ADMIN')
                    """);
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantAdminPostsCorrectsAndReconstructsTheIdempotentResult() throws Throwable {
        TenantPrincipal principal = new TestPrincipal();
        authenticate(principal);
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresOperatingCostStore store = new PostgresOperatingCostStore(
                    harness.jdbcTemplate());
            ScopeContext scope = ScopeContext.tenant(principal);

            harness.withinTenant(() -> {
                insertCommand(harness, ORIGINAL_COMMAND, "a", "/api/v1/cost-entries");
                insertCommand(
                        harness, CORRECTION_COMMAND, "b",
                        "/api/v1/cost-entries/{id}/corrections");
                assertThat(store.targetAvailable(
                        scope, CostTarget.domain(CostTarget.Type.FIELD, FIELD_ID))).isTrue();
                assertThat(store.targetAvailable(
                        scope,
                        CostTarget.domain(CostTarget.Type.FIELD, UUID.randomUUID()))).isFalse();

                var original = store.append(scope, original()).orElseThrow();
                assertThat(original.signedAmountVnd()).isEqualByComparingTo("1200000");
                var correction = store.appendCorrection(
                        scope, original.id(), reversal(), replacement()).orElseThrow();
                assertThat(correction.reversal().signedAmountVnd())
                        .isEqualByComparingTo("-1200000");
                assertThat(correction.replacement().target().id()).contains(SEASON_ID);
                assertThat(store.findCorrectionByReplacementId(
                        scope, correction.replacement().id())).contains(correction);

                UUID secondCommand = UUID.fromString(
                        "65000000-0000-0000-0000-000000000012");
                insertCommand(
                        harness, secondCommand, "c",
                        "/api/v1/cost-entries/{id}/corrections");
                assertThat(store.appendCorrection(
                        scope,
                        original.id(),
                        reversal(UUID.randomUUID(), secondCommand),
                        replacement(UUID.randomUUID(), secondCommand))).isEmpty();
                return null;
            });
        }
    }

    private OperatingCostEntry original() {
        return new OperatingCostEntry(
                ORIGINAL_ID, TENANT_ID, CostTarget.domain(CostTarget.Type.FIELD, FIELD_ID),
                CostCategory.LABOR, new BigDecimal("1200000"), CostEntryKind.POSTING,
                Instant.parse("2027-08-01T00:00:00Z"), Optional.of("Seasonal labor"),
                Optional.of("PAYROLL-08"), Optional.empty(), ORIGINAL_COMMAND, PROFILE_ID);
    }

    private OperatingCostEntry reversal() {
        return reversal(
                UUID.fromString("65000000-0000-0000-0000-000000000002"),
                CORRECTION_COMMAND);
    }

    private OperatingCostEntry reversal(UUID id, UUID commandId) {
        return new OperatingCostEntry(
                id, TENANT_ID, CostTarget.domain(CostTarget.Type.FIELD, FIELD_ID),
                CostCategory.LABOR, new BigDecimal("1200000"), CostEntryKind.REVERSAL,
                Instant.parse("2027-08-01T00:00:00Z"), Optional.of("Payroll correction"),
                Optional.of("PAYROLL-08"), Optional.of(ORIGINAL_ID), commandId, PROFILE_ID);
    }

    private OperatingCostEntry replacement() {
        return replacement(
                UUID.fromString("65000000-0000-0000-0000-000000000003"),
                CORRECTION_COMMAND);
    }

    private OperatingCostEntry replacement(UUID id, UUID commandId) {
        return new OperatingCostEntry(
                id, TENANT_ID, CostTarget.domain(CostTarget.Type.SEASON, SEASON_ID),
                CostCategory.LABOR, new BigDecimal("1150000"), CostEntryKind.POSTING,
                Instant.parse("2027-08-01T00:00:00Z"), Optional.of("Corrected payroll"),
                Optional.of("PAYROLL-08-R1"), Optional.empty(), commandId, PROFILE_ID);
    }

    private void insertCommand(
            TenantTransactionTestHarness harness,
            UUID commandId,
            String digestSeed,
            String route) {
        harness.jdbcTemplate().update("""
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                VALUES (?, ?, ?, 'POST', ?, repeat(?, 64), 1, repeat(?, 64), 'IN_PROGRESS')
                """, commandId, TENANT_ID, PROFILE_ID, route, digestSeed, digestSeed);
    }

    private void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
