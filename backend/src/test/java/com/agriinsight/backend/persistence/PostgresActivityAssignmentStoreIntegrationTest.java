package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityReadPageQuery;
import com.agriinsight.backend.operations.domain.ActivityAssignment;
import com.agriinsight.backend.operations.infrastructure.PostgresActivityAssignmentReadStore;
import com.agriinsight.backend.operations.infrastructure.PostgresActivityAssignmentStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
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
class PostgresActivityAssignmentStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_FARM_ID = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVITY_ID = UUID.fromString("41000000-0000-0000-0000-000000000007");
    private static final UUID OTHER_ACTIVITY_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000007");
    private static final UUID EMPLOYEE_ID = UUID.fromString("41000000-0000-0000-0000-000000000004");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("41000000-0000-0000-0000-000000000009");
    private static final UUID NEW_ASSIGNMENT_ID = UUID.fromString("55000000-0000-0000-0000-000000000001");
    private static final UUID TERMINAL_ASSIGNMENT_ID = UUID.fromString("55000000-0000-0000-0000-000000000002");

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
    void revokePreservesHistoryAndRegrantCreatesANewScopedRow() throws Throwable {
        TenantPrincipal principal = new TestPrincipal();
        authenticate(principal);
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresActivityAssignmentStore store =
                    new PostgresActivityAssignmentStore(harness.jdbcTemplate());
            PostgresActivityAssignmentReadStore reads =
                    new PostgresActivityAssignmentReadStore(harness.jdbcTemplate());
            ScopeContext farmScope = ScopeContext.domain(
                    principal, ScopeContext.Type.FARM, Optional.of(FARM_ID));
            ScopeContext wrongFarmScope = ScopeContext.domain(
                    principal, ScopeContext.Type.FARM, Optional.of(OTHER_FARM_ID));

            harness.withinTenant(() -> {
                assertThat(store.findById(farmScope, ASSIGNMENT_ID)).get()
                        .extracting(assignment -> assignment.active()).isEqualTo(true);
                assertThat(store.findById(wrongFarmScope, ASSIGNMENT_ID)).isEmpty();
                assertThat(store.activeEmployeeExists(farmScope, EMPLOYEE_ID)).isTrue();

                var revoked = store.revoke(farmScope, ASSIGNMENT_ID, 0).orElseThrow();
                assertThat(revoked.active()).isFalse();
                assertThat(revoked.version()).isEqualTo(1);

                var regranted = store.create(farmScope, new ActivityAssignment(
                        NEW_ASSIGNMENT_ID, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID)).orElseThrow();
                assertThat(regranted.active()).isTrue();
                assertThat(store.findActive(farmScope, ACTIVITY_ID, EMPLOYEE_ID))
                        .contains(regranted);
                var page = reads.findAll(
                        ScopeContext.domain(
                                principal,
                                ScopeContext.Type.ACTIVITY,
                                Optional.of(ACTIVITY_ID)),
                        ACTIVITY_ID,
                        Optional.of(EMPLOYEE_ID),
                        new ActivityReadPageQuery(1, 0));
                assertThat(page.items()).extracting(item -> item.id())
                        .containsExactly(NEW_ASSIGNMENT_ID);
                assertThat(page.hasMore()).isTrue();
                assertThat(reads.findAll(
                        ScopeContext.domain(
                                principal,
                                ScopeContext.Type.ACTIVITY,
                                Optional.of(OTHER_ACTIVITY_ID)),
                        OTHER_ACTIVITY_ID,
                        Optional.empty(),
                        new ActivityReadPageQuery(100, 0)).items()).isEmpty();
                assertThat(harness.jdbcTemplate().queryForObject("""
                        SELECT count(*) FROM activity_assignees
                         WHERE tenant_id = ? AND activity_id = ? AND employee_id = ?
                        """, Long.class, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID)).isEqualTo(2L);
                harness.jdbcTemplate().update("""
                        UPDATE activities
                           SET status = 'COMPLETED', completed_at = TIMESTAMPTZ '2027-09-01 02:30:00Z'
                         WHERE tenant_id = ? AND id = ?
                        """, TENANT_ID, ACTIVITY_ID);
                assertThat(store.create(farmScope, new ActivityAssignment(
                        TERMINAL_ASSIGNMENT_ID, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID))).isEmpty();
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

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
