package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.SeasonCommands;
import com.agriinsight.backend.farm.application.SeasonQuery;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.farm.infrastructure.PostgresSeasonStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class PostgresSeasonStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final UUID CROP_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID EXISTING_SEASON_ID = UUID.fromString("41000000-0000-0000-0000-000000000006");
    private static final UUID UNASSIGNED_FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000061");
    private static final UUID UNASSIGNED_FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000062");
    private static final UUID UNASSIGNED_SEASON_ID = UUID.fromString("41000000-0000-0000-0000-000000000063");
    private static final UUID NEW_SEASON_ID = UUID.fromString("41000000-0000-0000-0000-000000000064");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("SEASON_CHANGE"), Optional.of("request-store-1"));

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignmentScopePrecedesPagingAndVersionedStateTransitions() throws Throwable {
        authenticateFarmManager();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresSeasonStore store = new PostgresSeasonStore(harness.jdbcTemplate());
            ScopeContext listScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
            ScopeContext farmScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));

            harness.withinTenant(() -> {
                insertUnassignedSeason(harness);
                var managerPage = store.findAll(listScope, query(1));
                assertThat(managerPage.items()).extracting(item -> item.id())
                        .containsExactly(EXISTING_SEASON_ID);
                assertThat(managerPage.hasMore()).isFalse();
                assertThat(store.findById(listScope, UNASSIGNED_SEASON_ID)).isEmpty();
                assertThat(store.liveParentsAvailable(
                        farmScope, FARM_ID, FIELD_ID, CROP_ID, new BigDecimal("12.5"))).isTrue();
                assertThat(store.liveParentsAvailable(
                        farmScope, FARM_ID, FIELD_ID, CROP_ID, new BigDecimal("12.5001"))).isFalse();

                var created = store.create(farmScope, season());
                assertThat(created.status()).isEqualTo(Season.Status.PLANNED);
                assertThat(created.version()).isZero();

                var update = new SeasonCommands.Update(
                        Optional.empty(), Optional.of("Updated Season"), Optional.of(Optional.empty()),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(Optional.of(new BigDecimal("900000"))), 0, AUDIT);
                var updated = store.update(farmScope, NEW_SEASON_ID, 0, update).orElseThrow();
                assertThat(updated.displayName()).isEqualTo("Updated Season");
                assertThat(updated.varietyName()).isEmpty();
                assertThat(updated.version()).isEqualTo(1);
                assertThat(store.update(farmScope, NEW_SEASON_ID, 0, update)).isEmpty();

                var active = store.transition(
                        farmScope, NEW_SEASON_ID, 1, Season.Status.PLANNED,
                        Season.Status.ACTIVE, LocalDate.parse("2028-01-02")).orElseThrow();
                assertThat(active.status()).isEqualTo(Season.Status.ACTIVE);
                assertThat(active.startedOn()).contains(LocalDate.parse("2028-01-02"));

                var completed = store.transition(
                        farmScope, NEW_SEASON_ID, 2, Season.Status.ACTIVE,
                        Season.Status.COMPLETED, LocalDate.parse("2028-11-30")).orElseThrow();
                assertThat(completed.status()).isEqualTo(Season.Status.COMPLETED);
                assertThat(completed.endedOn()).contains(LocalDate.parse("2028-11-30"));
                return null;
            });
        }
    }

    private void insertUnassignedSeason(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO farms (id, tenant_id, code, display_name)
                VALUES (?, ?, 'UNASSIGNED-SEASON-FARM', 'Unassigned Season Farm')
                """, UNASSIGNED_FARM_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES (?, ?, ?, 'UNASSIGNED-SEASON-FIELD', 'Unassigned Season Field', 5.0000)
                """, UNASSIGNED_FIELD_ID, TENANT_ID, UNASSIGNED_FARM_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares)
                VALUES (?, ?, ?, ?, ?, 'UNASSIGNED-SEASON', 'Unassigned Season',
                        DATE '2030-01-01', DATE '2030-12-31', 4.0000)
                """, UNASSIGNED_SEASON_ID, TENANT_ID, UNASSIGNED_FARM_ID, UNASSIGNED_FIELD_ID, CROP_ID);
    }

    private Season season() {
        return new Season(
                NEW_SEASON_ID, TENANT_ID, FARM_ID, FIELD_ID, CROP_ID,
                "SEASON-2028", "Season 2028", Optional.of("Arabica"),
                LocalDate.parse("2028-01-01"), LocalDate.parse("2028-12-31"),
                new BigDecimal("8.5"), Optional.of(new BigDecimal("1000000")));
    }

    private SeasonQuery query(int limit) {
        return new SeasonQuery(
                limit, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private void authenticateFarmManager() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority(Role.FARM_MANAGER.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
