package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.WarehouseAssignment;
import com.agriinsight.backend.inventory.infrastructure.PostgresWarehouseAssignmentStore;
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
class PostgresWarehouseAssignmentStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID WAREHOUSE_ID = UUID.fromString("57000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("57000000-0000-0000-0000-000000000002");
    private static final UUID DUPLICATE_ID = UUID.fromString("57000000-0000-0000-0000-000000000003");
    private static final UUID REGRANT_ID = UUID.fromString("57000000-0000-0000-0000-000000000004");
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
    void grantConflictRevocationAndRegrantPreserveHistory() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            var store = new PostgresWarehouseAssignmentStore(harness.jdbcTemplate());
            ScopeContext scope = ScopeContext.tenant(PRINCIPAL);

            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO warehouses (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'WH-ASSIGN', 'Assignment Warehouse')
                        """, WAREHOUSE_ID, TENANT_ID);

                assertThat(store.activeProfileExists(scope, PROFILE_ID)).isTrue();
                assertThat(store.activeWarehouseExists(scope, WAREHOUSE_ID)).isTrue();
                var created = store.create(scope, assignment(ASSIGNMENT_ID)).orElseThrow();
                assertThat(created.active()).isTrue();
                assertThat(created.version()).isZero();
                assertThat(store.findActive(scope, PROFILE_ID, WAREHOUSE_ID)).isPresent();

                assertThat(store.create(scope, assignment(DUPLICATE_ID))).isEmpty();
                assertThat(store.revoke(scope, ASSIGNMENT_ID, 0)).get().satisfies(revoked -> {
                    assertThat(revoked.active()).isFalse();
                    assertThat(revoked.version()).isEqualTo(1);
                });
                assertThat(store.findActive(scope, PROFILE_ID, WAREHOUSE_ID)).isEmpty();

                var regranted = store.create(scope, assignment(REGRANT_ID)).orElseThrow();
                assertThat(regranted.id()).isEqualTo(REGRANT_ID);
                assertThat(store.findById(scope, ASSIGNMENT_ID)).get()
                        .extracting(item -> item.active()).isEqualTo(false);
                return null;
            });
        }
    }

    private WarehouseAssignment assignment(UUID id) {
        return new WarehouseAssignment(id, TENANT_ID, PROFILE_ID, WAREHOUSE_ID);
    }

    private void authenticateTenantAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
