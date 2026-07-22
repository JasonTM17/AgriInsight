package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.cost.application.CostSummaryGroup;
import com.agriinsight.backend.cost.application.CostSummaryQuery;
import com.agriinsight.backend.cost.application.OperatingCostQuery;
import com.agriinsight.backend.cost.infrastructure.PostgresOperatingCostQueryStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
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
class PostgresOperatingCostQueryStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000005");
    private static final UUID TENANT_ENTRY_ID = UUID.fromString(
            "66000000-0000-0000-0000-000000000004");
    private static final Instant FROM = Instant.parse("2027-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-10-01T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, seedSql());
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void farmAndTenantReadersReceiveBoundedCanonicalSummaries() throws Throwable {
        TenantPrincipal principal = new TestPrincipal();
        authenticate(principal, "ROLE_FARM_MANAGER");
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresOperatingCostQueryStore store =
                    new PostgresOperatingCostQueryStore(harness.jdbcTemplate());
            ScopeContext farmScope = ScopeContext.domain(
                    principal, ScopeContext.Type.FARM, Optional.empty());
            harness.withinTenant(() -> {
                var page = store.findAll(farmScope, query());
                assertThat(page.items()).hasSize(3);
                assertThat(page.items()).extracting(entry -> entry.id().toString())
                        .containsExactly(
                                "66000000-0000-0000-0000-000000000003",
                                "66000000-0000-0000-0000-000000000002",
                                "66000000-0000-0000-0000-000000000001");
                assertThat(store.findById(farmScope, TENANT_ENTRY_ID)).isEmpty();
                ScopeContext unrelatedFarmScope = ScopeContext.domain(
                        principal, ScopeContext.Type.FARM, Optional.of(UUID.fromString(
                                "66000000-0000-0000-0000-000000000099")));
                assertThat(store.findById(
                        unrelatedFarmScope,
                        UUID.fromString("66000000-0000-0000-0000-000000000001")))
                        .isEmpty();

                var month = store.summarize(farmScope, summary(CostSummaryGroup.MONTH));
                assertThat(month.items()).singleElement().satisfies(item -> {
                    assertThat(item.groupKey()).isEqualTo("2027-08");
                    assertThat(item.netOperatingCostVnd()).isEqualByComparingTo("1150000");
                });
                var season = store.summarize(farmScope, summary(CostSummaryGroup.SEASON));
                assertThat(season.items()).singleElement().satisfies(item -> {
                    assertThat(item.seasonBudgetVnd()).contains(
                            new java.math.BigDecimal("2000000.00"));
                    assertThat(item.budgetVarianceVnd()).contains(
                            new java.math.BigDecimal("850000.00"));
                });
                return null;
            });
        }

        grantExecutive();
        authenticate(principal, "ROLE_EXECUTIVE");
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresOperatingCostQueryStore store =
                    new PostgresOperatingCostQueryStore(harness.jdbcTemplate());
            harness.withinTenant(() -> {
                ScopeContext tenantScope = ScopeContext.tenant(principal);
                assertThat(store.findAll(tenantScope, query()).items()).hasSize(4);
                assertThat(store.summarize(
                        tenantScope, summary(CostSummaryGroup.MONTH)).items())
                        .extracting(item -> item.groupKey())
                        .containsExactly("2027-08", "2027-09");
                return null;
            });
        }
    }

    private OperatingCostQuery query() {
        return new OperatingCostQuery(
                25, 0, FROM, TO, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private CostSummaryQuery summary(CostSummaryGroup group) {
        return new CostSummaryQuery(
                FROM, TO, group, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static void grantExecutive() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                    VALUES ('66000000-0000-0000-0000-000000000021',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000005', 'EXECUTIVE')
                    """);
        }
    }

    private static String seedSql() {
        return """
                INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                VALUES ('66000000-0000-0000-0000-000000000020',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000005', 'FARM_MANAGER');
                UPDATE seasons SET budget_vnd = 2000000.00
                 WHERE id = '41000000-0000-0000-0000-000000000006';
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                SELECT command_id, '10000000-0000-0000-0000-000000000041',
                       '41000000-0000-0000-0000-000000000005', 'POST', route,
                       repeat(seed, 64), 1, repeat(seed, 64), 'IN_PROGRESS'
                  FROM (VALUES
                    ('66000000-0000-0000-0000-000000000010'::uuid, 'a', '/api/v1/cost-entries'),
                    ('66000000-0000-0000-0000-000000000011'::uuid, 'b', '/api/v1/cost-entries/{id}/corrections'),
                    ('66000000-0000-0000-0000-000000000012'::uuid, 'c', '/api/v1/cost-entries')
                  ) commands(command_id, seed, route);
                INSERT INTO operating_cost_entries (
                    id, tenant_id, target_type, field_id, season_id, category_code,
                    amount_vnd, entry_kind, occurred_at, reversal_of,
                    command_reference, recorded_by_profile_id) VALUES
                    ('66000000-0000-0000-0000-000000000001',
                     '10000000-0000-0000-0000-000000000041', 'FIELD',
                     '41000000-0000-0000-0000-000000000003', NULL, 'LABOR',
                     1200000, 'POSTING', '2027-08-01T00:00:00Z', NULL,
                     '66000000-0000-0000-0000-000000000010',
                     '41000000-0000-0000-0000-000000000005'),
                    ('66000000-0000-0000-0000-000000000002',
                     '10000000-0000-0000-0000-000000000041', 'FIELD',
                     '41000000-0000-0000-0000-000000000003', NULL, 'LABOR',
                     1200000, 'REVERSAL', '2027-08-01T00:00:00Z',
                     '66000000-0000-0000-0000-000000000001',
                     '66000000-0000-0000-0000-000000000011',
                     '41000000-0000-0000-0000-000000000005'),
                    ('66000000-0000-0000-0000-000000000003',
                     '10000000-0000-0000-0000-000000000041', 'SEASON', NULL,
                     '41000000-0000-0000-0000-000000000006', 'LABOR',
                     1150000, 'POSTING', '2027-08-01T00:00:00Z', NULL,
                     '66000000-0000-0000-0000-000000000011',
                     '41000000-0000-0000-0000-000000000005'),
                    ('66000000-0000-0000-0000-000000000004',
                     '10000000-0000-0000-0000-000000000041', 'TENANT', NULL, NULL, 'OTHER',
                     500000, 'POSTING', '2027-09-01T00:00:00Z', NULL,
                     '66000000-0000-0000-0000-000000000012',
                     '41000000-0000-0000-0000-000000000005');
                """;
    }

    private void authenticate(TenantPrincipal principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, java.util.List.of(new SimpleGrantedAuthority(role))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
