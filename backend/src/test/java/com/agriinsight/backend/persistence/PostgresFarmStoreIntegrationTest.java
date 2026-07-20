package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FarmQuery;
import com.agriinsight.backend.farm.domain.Farm;
import com.agriinsight.backend.farm.infrastructure.PostgresFarmStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
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
class PostgresFarmStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID ASSIGNED_FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID UNASSIGNED_FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000021");
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
    void assignmentPredicateRunsBeforePagingAndVersionedMutation() throws Throwable {
        authenticateFarmManager();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresFarmStore store = new PostgresFarmStore(harness.jdbcTemplate());
            ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);
            ScopeContext listScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
            ScopeContext assignedScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.of(ASSIGNED_FARM_ID));
            ScopeContext unassignedScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.of(UNASSIGNED_FARM_ID));

            harness.withinTenant(() -> {
                store.create(tenantScope, new Farm(
                        UNASSIGNED_FARM_ID, TENANT_ID, "UNASSIGNED", "AAA Unassigned Farm"));

                var managerPage = store.findAll(listScope, query());
                assertThat(managerPage.items()).extracting(item -> item.id())
                        .containsExactly(ASSIGNED_FARM_ID);
                assertThat(managerPage.hasMore()).isFalse();
                assertThat(store.findById(unassignedScope, UNASSIGNED_FARM_ID)).isEmpty();
                assertThat(store.update(
                        unassignedScope,
                        UNASSIGNED_FARM_ID,
                        0,
                        Optional.empty(),
                        Optional.of("Leaked update"))).isEmpty();

                var updated = store.update(
                        assignedScope,
                        ASSIGNED_FARM_ID,
                        0,
                        Optional.empty(),
                        Optional.of("Assigned Farm Updated"));
                assertThat(updated).get().extracting(item -> item.version()).isEqualTo(1L);

                var codeOnlyUpdate = store.update(
                        assignedScope,
                        ASSIGNED_FARM_ID,
                        1,
                        Optional.of("FARM-A-UPDATED"),
                        Optional.empty());
                assertThat(codeOnlyUpdate).get().satisfies(item -> {
                    assertThat(item.code()).isEqualTo("FARM-A-UPDATED");
                    assertThat(item.version()).isEqualTo(2L);
                });
                assertThat(store.update(
                        assignedScope,
                        ASSIGNED_FARM_ID,
                        1,
                        Optional.empty(),
                        Optional.of("Stale update"))).isEmpty();
                assertThat(store.update(
                        assignedScope,
                        ASSIGNED_FARM_ID,
                        2,
                        Optional.of("FARM-A-UPDATED"),
                        Optional.empty())).isEmpty();
                assertThat(store.updateActive(
                        assignedScope,
                        ASSIGNED_FARM_ID,
                        2,
                        true)).isEmpty();

                harness.jdbcTemplate().update("""
                        INSERT INTO user_roles (
                            id, tenant_id, user_profile_id, role_code)
                        VALUES (?, ?, ?, 'TENANT_ADMIN')
                        """,
                        UUID.fromString("41000000-0000-0000-0000-000000000022"),
                        TENANT_ID,
                        PROFILE_ID);
                assertThat(store.findAll(listScope, query()).items())
                        .extracting(item -> item.id())
                        .containsExactly(ASSIGNED_FARM_ID);
                assertThat(store.findAll(tenantScope, tenantQuery()).items())
                        .extracting(item -> item.id())
                        .containsExactly(UNASSIGNED_FARM_ID, ASSIGNED_FARM_ID);
                return null;
            });
        }
    }

    private FarmQuery query() {
        return new FarmQuery(1, 0, Optional.empty(), Optional.empty());
    }

    private FarmQuery tenantQuery() {
        return new FarmQuery(25, 0, Optional.empty(), Optional.empty());
    }

    private void authenticateFarmManager() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.FARM_MANAGER.authority()))));
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
