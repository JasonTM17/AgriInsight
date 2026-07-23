package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FarmAssignmentQuery;
import com.agriinsight.backend.farm.domain.FarmAssignment;
import com.agriinsight.backend.farm.infrastructure.PostgresFarmAssignmentStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresFarmAssignmentStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID EXISTING_ID = UUID.fromString("41000000-0000-0000-0000-000000000008");
    private static final UUID NEW_PROFILE_ID = UUID.fromString("4c000000-0000-0000-0000-000000000001");
    private static final UUID NEW_ASSIGNMENT_ID = UUID.fromString("4c000000-0000-0000-0000-000000000002");
    private static final UUID REGRANT_ID = UUID.fromString("4c000000-0000-0000-0000-000000000003");
    private static final UUID INACTIVE_PROFILE_ID =
            UUID.fromString("4c000000-0000-0000-0000-000000000011");
    private static final UUID INVALID_ASSIGNMENT_ID =
            UUID.fromString("4c000000-0000-0000-0000-000000000012");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();

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
    void tenantStorePreservesRevocationHistoryAndAllowsANewGrant() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            var store = new PostgresFarmAssignmentStore(harness.jdbcTemplate());
            ScopeContext scope = ScopeContext.tenant(PRINCIPAL);

            harness.withinTenant(() -> {
                assertThat(store.findById(scope, EXISTING_ID)).get()
                        .extracting(item -> item.active()).isEqualTo(true);
                assertThat(store.findActive(scope, PROFILE_ID, FARM_ID)).isPresent();
                assertThat(store.activeProfileExists(scope, PROFILE_ID)).isTrue();
                assertThat(store.activeFarmExists(scope, FARM_ID)).isTrue();

                harness.jdbcTemplate().update("""
                        INSERT INTO user_profiles (id, tenant_id, display_name)
                        VALUES (?, ?, 'Second Farm Manager')
                        """, NEW_PROFILE_ID, TENANT_ID);
                var created = store.create(scope, new FarmAssignment(
                        NEW_ASSIGNMENT_ID, TENANT_ID, NEW_PROFILE_ID, FARM_ID));
                assertThat(created.active()).isTrue();
                assertThat(created.version()).isZero();

                var revoked = store.revoke(scope, NEW_ASSIGNMENT_ID, 0).orElseThrow();
                assertThat(revoked.active()).isFalse();
                assertThat(revoked.version()).isEqualTo(1);
                assertThat(store.findActive(scope, NEW_PROFILE_ID, FARM_ID)).isEmpty();

                var regranted = store.create(scope, new FarmAssignment(
                        REGRANT_ID, TENANT_ID, NEW_PROFILE_ID, FARM_ID));
                assertThat(regranted.id()).isEqualTo(REGRANT_ID);
                assertThat(store.findById(scope, NEW_ASSIGNMENT_ID)).get()
                        .extracting(item -> item.active()).isEqualTo(false);

                var firstPage = store.findAll(scope, new FarmAssignmentQuery(
                        1,
                        0,
                        Optional.of(NEW_PROFILE_ID),
                        Optional.of(FARM_ID),
                        Optional.empty()));
                assertThat(firstPage.items()).extracting(item -> item.id())
                        .containsExactly(NEW_ASSIGNMENT_ID);
                assertThat(firstPage.hasMore()).isTrue();
                assertThat(store.findAll(scope, new FarmAssignmentQuery(
                        100,
                        0,
                        Optional.of(NEW_PROFILE_ID),
                        Optional.of(FARM_ID),
                        Optional.of(true))).items())
                        .extracting(item -> item.id())
                        .containsExactly(REGRANT_ID);
                return null;
            });
        }
    }

    @Test
    void inactiveOrCrossTenantTargetsCannotBecomeVisibleAssignments() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            var store = new PostgresFarmAssignmentStore(harness.jdbcTemplate());
            ScopeContext scope = ScopeContext.tenant(PRINCIPAL);

            harness.withinTenant(() -> {
                assertThat(store.findById(
                        scope, UUID.fromString("42000000-0000-0000-0000-000000000008")))
                        .isEmpty();
                assertThat(store.findAll(scope, new FarmAssignmentQuery(
                        100,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())).items())
                        .allMatch(item -> item.tenantId().equals(TENANT_ID));
                harness.jdbcTemplate().update("""
                        INSERT INTO user_profiles (id, tenant_id, display_name, active)
                        VALUES (?, ?, 'Inactive Farm Manager', FALSE)
                        """, INACTIVE_PROFILE_ID, TENANT_ID);
                assertThat(store.activeProfileExists(scope, INACTIVE_PROFILE_ID)).isFalse();
                assertThatThrownBy(() -> store.create(scope, new FarmAssignment(
                        INVALID_ASSIGNMENT_ID, TENANT_ID, INACTIVE_PROFILE_ID, FARM_ID)))
                        .isInstanceOf(DataIntegrityViolationException.class);
                return null;
            });
        }
    }

    private void authenticateTenantAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return profileId().toString(); }
    }
}
