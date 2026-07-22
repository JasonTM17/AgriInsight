package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CostCorrectionConcurrencyIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000001");
    private static final UUID ORIGINAL_ID = UUID.fromString(
            "68000000-0000-0000-0000-000000000001");
    private static final ScopeContext SCOPE = ScopeContext.tenant(new TestPrincipal());

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

    @Test
    void concurrentCorrectionsAppendExactlyOneReversalAndReplacement() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> correct(
                    UUID.fromString("68000000-0000-0000-0000-000000000011"),
                    "68000000-0000-0000-0000-000000000002",
                    "68000000-0000-0000-0000-000000000003", ready, start));
            var second = executor.submit(() -> correct(
                    UUID.fromString("68000000-0000-0000-0000-000000000012"),
                    "68000000-0000-0000-0000-000000000004",
                    "68000000-0000-0000-0000-000000000005", ready, start));
            ready.await();
            start.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM operating_cost_entries
                     WHERE reversal_of = '68000000-0000-0000-0000-000000000001'
                    """)).isEqualTo(1);
            assertThat(count(operator, "SELECT count(*) FROM operating_cost_entries"))
                    .isEqualTo(3);
        }
    }

    private boolean correct(
            UUID commandId,
            String reversalId,
            String replacementId,
            CountDownLatch ready,
            CountDownLatch start) {
        authenticate();
        ready.countDown();
        try {
            start.await();
            try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                    POSTGRESQL, "agriinsight")) {
                PostgresOperatingCostStore store = new PostgresOperatingCostStore(
                        harness.jdbcTemplate());
                return harness.withinTenant(() -> store.appendCorrection(
                        SCOPE,
                        ORIGINAL_ID,
                        entry(UUID.fromString(reversalId), commandId, CostEntryKind.REVERSAL),
                        entry(UUID.fromString(replacementId), commandId, CostEntryKind.POSTING)))
                        .isPresent();
            }
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private OperatingCostEntry entry(UUID id, UUID commandId, CostEntryKind kind) {
        return new OperatingCostEntry(
                id, TENANT_ID, CostTarget.domain(CostTarget.Type.FARM, FARM_ID),
                CostCategory.LABOR,
                kind == CostEntryKind.REVERSAL
                        ? new BigDecimal("100000") : new BigDecimal("95000"),
                kind, Instant.parse("2027-09-01T00:00:00Z"),
                Optional.of("Concurrent correction"), Optional.empty(),
                kind == CostEntryKind.REVERSAL ? Optional.of(ORIGINAL_ID) : Optional.empty(),
                commandId, PROFILE_ID);
    }

    private static String seedSql() {
        return """
                INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                VALUES ('68000000-0000-0000-0000-000000000020',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000005', 'TENANT_ADMIN');
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                SELECT command_id, '10000000-0000-0000-0000-000000000041',
                       '41000000-0000-0000-0000-000000000005', 'POST', route,
                       repeat(seed, 64), 1, repeat(seed, 64), 'IN_PROGRESS'
                  FROM (VALUES
                    ('68000000-0000-0000-0000-000000000010'::uuid, 'a', '/api/v1/cost-entries'),
                    ('68000000-0000-0000-0000-000000000011'::uuid, 'b', '/api/v1/cost-entries/{id}/corrections'),
                    ('68000000-0000-0000-0000-000000000012'::uuid, 'c', '/api/v1/cost-entries/{id}/corrections')
                  ) commands(command_id, seed, route);
                INSERT INTO operating_cost_entries (
                    id, tenant_id, target_type, farm_id, category_code, amount_vnd,
                    entry_kind, occurred_at, command_reference, recorded_by_profile_id)
                VALUES ('68000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041', 'FARM',
                        '41000000-0000-0000-0000-000000000001', 'LABOR', 100000,
                        'POSTING', '2027-09-01T00:00:00Z',
                        '68000000-0000-0000-0000-000000000010',
                        '41000000-0000-0000-0000-000000000005');
                """;
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new TestPrincipal(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
