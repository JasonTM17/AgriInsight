package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.EmployeeCommands;
import com.agriinsight.backend.operations.application.EmployeeQuery;
import com.agriinsight.backend.operations.domain.Employee;
import com.agriinsight.backend.operations.infrastructure.PostgresEmployeeStore;
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
class PostgresEmployeeStoreIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID EXISTING_EMPLOYEE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000004");
    private static final UUID NEW_EMPLOYEE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000081");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("EMPLOYEE_CHANGE"), Optional.of("request-store-4"));

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
    void tenantScopeOwnsMasterDataWhileFarmScopeOnlyReadsEligibleProjection() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresEmployeeStore store = new PostgresEmployeeStore(harness.jdbcTemplate());
            ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);
            ScopeContext farmListScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());

            harness.withinTenant(() -> {
                assertThat(store.findAll(tenantScope, query(Optional.empty(), Optional.empty())).items())
                        .extracting(item -> item.id())
                        .contains(EXISTING_EMPLOYEE_ID);
                assertThatThrownBy(() -> store.findAll(
                        farmListScope, query(Optional.empty(), Optional.empty())))
                        .isInstanceOf(IllegalArgumentException.class);

                var created = store.create(tenantScope, new Employee(
                        NEW_EMPLOYEE_ID, TENANT_ID, "WORKER-B", "Worker B",
                        Optional.of("Technician")));
                assertThat(created.active()).isTrue();
                assertThat(store.findEligible(
                        farmListScope, query(Optional.of(false), Optional.of("worker-b"))).items())
                        .extracting(item -> item.id())
                        .containsExactly(NEW_EMPLOYEE_ID);

                var updated = store.update(
                        tenantScope, NEW_EMPLOYEE_ID, 0,
                        new EmployeeCommands.Update(
                                Optional.empty(), Optional.of("Worker B Updated"),
                                Optional.of(Optional.empty()), 0, AUDIT))
                        .orElseThrow();
                assertThat(updated.displayName()).isEqualTo("Worker B Updated");
                assertThat(updated.jobTitle()).isEmpty();
                assertThat(updated.version()).isEqualTo(1);
                assertThat(store.updateActive(tenantScope, NEW_EMPLOYEE_ID, 1, false))
                        .get().extracting(item -> item.active()).isEqualTo(false);
                assertThat(store.findEligible(
                        farmListScope, query(Optional.empty(), Optional.of("worker-b"))).items())
                        .isEmpty();

                assertThat(store.hasDeactivationBlockers(tenantScope, EXISTING_EMPLOYEE_ID)).isTrue();
                assertThat(store.updateActive(tenantScope, EXISTING_EMPLOYEE_ID, 0, false)).isEmpty();
                return null;
            });
        }
    }

    private EmployeeQuery query(Optional<Boolean> active, Optional<String> search) {
        return new EmployeeQuery(25, 0, active, search);
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
