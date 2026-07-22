package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityCommands;
import com.agriinsight.backend.operations.application.ActivityQuery;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import com.agriinsight.backend.operations.infrastructure.PostgresActivityStore;
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
class PostgresActivityStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID WORKER_PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID MANAGER_PROFILE_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final UUID SEASON_ID = UUID.fromString("41000000-0000-0000-0000-000000000006");
    private static final UUID EXISTING_ACTIVITY_ID = UUID.fromString("41000000-0000-0000-0000-000000000007");
    private static final UUID NEW_ACTIVITY_ID = UUID.fromString("54000000-0000-0000-0000-000000000004");
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("ACTIVITY_CHANGE"), Optional.of("request-store-activity"));

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
    void managerMutationsAndWorkerReadsRemainBoundToTheirOwnGrants() throws Throwable {
        TenantPrincipal worker = new TestPrincipal(WORKER_PROFILE_ID);
        TenantPrincipal manager = new TestPrincipal(MANAGER_PROFILE_ID);
        authenticate(manager);
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresActivityStore store = new PostgresActivityStore(harness.jdbcTemplate());

            harness.withinTenant(() -> {
                seedActiveRolesAndManager(harness);
                ScopeContext tenantScope = ScopeContext.tenant(manager);
                ScopeContext managerFarmScope = ScopeContext.domain(
                        manager, ScopeContext.Type.FARM, Optional.of(FARM_ID));
                ScopeContext managerActivityScope = ScopeContext.domain(
                        manager, ScopeContext.Type.ACTIVITY, Optional.empty());
                ScopeContext workerActivityScope = ScopeContext.domain(
                        worker, ScopeContext.Type.ACTIVITY, Optional.empty());

                assertThat(store.findAll(workerActivityScope, query()).items())
                        .extracting(item -> item.id())
                        .containsExactly(EXISTING_ACTIVITY_ID);
                assertThat(store.liveParentsAvailable(
                        managerFarmScope, FARM_ID, FIELD_ID, SEASON_ID, ActivityType.HARVEST)).isTrue();

                var created = store.create(managerFarmScope, new Activity(
                        NEW_ACTIVITY_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID,
                        ActivityType.HARVEST, " harvest-followup ", "Harvest follow-up",
                        Optional.of("North block"), Instant.parse("2027-09-03T01:00:00Z"),
                        Instant.parse("2027-09-03T03:00:00Z"))).orElseThrow();
                assertThat(created.code()).isEqualTo("HARVEST-FOLLOWUP");

                var updated = store.update(
                        managerFarmScope, NEW_ACTIVITY_ID, 0,
                        new ActivityCommands.Update(
                                Optional.empty(), Optional.empty(), Optional.of("Harvest inspection"),
                                Optional.of(Optional.empty()), Optional.empty(), Optional.empty(), 0, AUDIT))
                        .orElseThrow();
                assertThat(updated.title()).isEqualTo("Harvest inspection");
                assertThat(updated.description()).isEmpty();

                var started = store.transition(
                        managerFarmScope, NEW_ACTIVITY_ID, 1,
                        ActivityStatus.PLANNED, ActivityStatus.STARTED,
                        Instant.parse("2027-09-03T01:15:00Z")).orElseThrow();
                var completed = store.transition(
                        managerFarmScope, NEW_ACTIVITY_ID, 2,
                        ActivityStatus.STARTED, ActivityStatus.COMPLETED,
                        Instant.parse("2027-09-03T02:45:00Z")).orElseThrow();
                assertThat(started.status()).isEqualTo(ActivityStatus.STARTED);
                assertThat(completed.status()).isEqualTo(ActivityStatus.COMPLETED);
                assertThat(completed.version()).isEqualTo(3);

                assertThat(store.findAll(managerActivityScope, query()).items())
                        .extracting(item -> item.id())
                        .contains(EXISTING_ACTIVITY_ID, NEW_ACTIVITY_ID);
                assertThat(store.findAll(managerActivityScope, queryWithSearch("inspection")).items())
                        .extracting(item -> item.id())
                        .containsExactly(NEW_ACTIVITY_ID);
                assertThat(store.findAll(workerActivityScope, query()).items())
                        .extracting(item -> item.id())
                        .containsExactly(EXISTING_ACTIVITY_ID);
                assertThat(store.findById(workerActivityScope, NEW_ACTIVITY_ID)).isEmpty();
                assertThat(store.findById(tenantScope, NEW_ACTIVITY_ID)).isPresent();
                return null;
            });
        }
    }

    private void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));
    }

    private void seedActiveRolesAndManager(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                VALUES ('54000000-0000-0000-0000-000000000011', ?, ?, 'FIELD_WORKER')
                """, TENANT_ID, WORKER_PROFILE_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO user_profiles (id, tenant_id, display_name)
                VALUES (?, ?, 'Farm Manager')
                """, MANAGER_PROFILE_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                VALUES ('54000000-0000-0000-0000-000000000012', ?, ?, 'FARM_MANAGER')
                """, TENANT_ID, MANAGER_PROFILE_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                VALUES ('54000000-0000-0000-0000-000000000013', ?, ?, ?)
                """, TENANT_ID, MANAGER_PROFILE_ID, FARM_ID);
    }

    private ActivityQuery query() {
        return new ActivityQuery(
                25, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private ActivityQuery queryWithSearch(String search) {
        return new ActivityQuery(
                25, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(search));
    }

    private record TestPrincipal(UUID profileId) implements TenantPrincipal {
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return profileId.toString(); }
    }
}
