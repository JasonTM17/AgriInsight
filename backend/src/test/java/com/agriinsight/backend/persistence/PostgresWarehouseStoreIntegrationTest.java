package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.WarehouseQuery;
import com.agriinsight.backend.inventory.domain.Warehouse;
import com.agriinsight.backend.inventory.infrastructure.PostgresWarehouseStore;
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
class PostgresWarehouseStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID ASSIGNED_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID UNASSIGNED_ID = UUID.fromString("51000000-0000-0000-0000-000000000002");
    private static final UUID MATERIAL_ID = UUID.fromString("51000000-0000-0000-0000-000000000003");
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
    void assignmentFilteringVersionedMutationAndLifecycleFailClosed() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresWarehouseStore store = new PostgresWarehouseStore(harness.jdbcTemplate());
            ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);
            ScopeContext listScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.empty());
            ScopeContext assignedScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.of(ASSIGNED_ID));
            ScopeContext unassignedScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.of(UNASSIGNED_ID));

            harness.withinTenant(() -> {
                insertTenantAdminRole(harness);
                store.create(tenantScope, warehouse(ASSIGNED_ID, "WH-ASSIGNED", "BBB Assigned"));
                store.create(tenantScope, warehouse(UNASSIGNED_ID, "WH-UNASSIGNED", "AAA Unassigned"));
                insertAssignment(harness);

                assertThat(store.findAll(listScope, query(1)).items())
                        .extracting(item -> item.id())
                        .containsExactly(ASSIGNED_ID);
                assertThat(store.findAll(listScope, query(1)).hasMore()).isFalse();
                assertThat(store.findById(unassignedScope, UNASSIGNED_ID)).isEmpty();
                assertThat(store.update(
                        unassignedScope, UNASSIGNED_ID, 0,
                        Optional.empty(), Optional.of("Leaked update"), Optional.empty())).isEmpty();

                assertThat(store.update(
                        assignedScope, ASSIGNED_ID, 0,
                        Optional.empty(), Optional.of("Assigned Updated"), Optional.empty()))
                        .get().extracting(item -> item.version()).isEqualTo(1L);
                assertThat(store.update(
                        assignedScope, ASSIGNED_ID, 1,
                        Optional.empty(), Optional.empty(), Optional.of(Optional.empty())))
                        .get().satisfies(item -> {
                            assertThat(item.locationText()).isEmpty();
                            assertThat(item.version()).isEqualTo(2L);
                        });
                assertThat(store.update(
                        assignedScope, ASSIGNED_ID, 2,
                        Optional.empty(), Optional.empty(), Optional.of(Optional.empty()))).isEmpty();
                assertThat(store.update(
                        assignedScope, ASSIGNED_ID, 1,
                        Optional.empty(), Optional.of("Stale"), Optional.empty())).isEmpty();
                assertThatThrownBy(() -> store.updateActive(
                        assignedScope, ASSIGNED_ID, 2, false))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("tenant-wide");

                assertThat(store.findAll(tenantScope, query(25)).items())
                        .extracting(item -> item.id())
                        .containsExactly(UNASSIGNED_ID, ASSIGNED_ID);
                assertThat(store.hasDeactivationBlockers(tenantScope, ASSIGNED_ID)).isTrue();
                assertThat(store.updateActive(tenantScope, ASSIGNED_ID, 2, false)).isEmpty();

                insertStockAndHistory(harness);
                assertThat(store.hasDeactivationBlockers(tenantScope, ASSIGNED_ID)).isTrue();
                assertThat(store.updateActive(tenantScope, ASSIGNED_ID, 2, false)).isEmpty();
                clearStock(harness);
                revokeAssignment(harness);
                assertThat(store.hasDeactivationBlockers(tenantScope, ASSIGNED_ID)).isTrue();
                assertThat(store.updateActive(tenantScope, ASSIGNED_ID, 2, false)).isEmpty();
                assertThat(store.hasDeactivationBlockers(tenantScope, UNASSIGNED_ID)).isFalse();
                assertThat(store.updateActive(tenantScope, UNASSIGNED_ID, 0, false))
                        .get().satisfies(item -> {
                            assertThat(item.active()).isFalse();
                            assertThat(item.version()).isEqualTo(1L);
                        });
                return null;
            });
        }
    }

    private Warehouse warehouse(UUID id, String code, String displayName) {
        return new Warehouse(id, TENANT_ID, code, displayName, Optional.of("Central Highlands"));
    }

    private WarehouseQuery query(int limit) {
        return new WarehouseQuery(limit, 0, Optional.empty(), Optional.empty());
    }

    private void insertAssignment(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO user_warehouse_assignments (
                    id, tenant_id, user_profile_id, warehouse_id)
                VALUES (?, ?, ?, ?)
                """,
                UUID.fromString("51000000-0000-0000-0000-000000000004"),
                TENANT_ID, PROFILE_ID, ASSIGNED_ID);
    }

    private void insertTenantAdminRole(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                VALUES (?, ?, ?, 'TENANT_ADMIN')
                """, UUID.fromString("51000000-0000-0000-0000-000000000007"),
                TENANT_ID, PROFILE_ID);
    }

    private void insertStockAndHistory(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO materials (id, tenant_id, code, display_name, base_unit)
                VALUES (?, ?, 'FERTILIZER', 'Fertilizer', 'KG')
                """, MATERIAL_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO inventory_transactions (
                    id, tenant_id, warehouse_id, material_id, kind, unit_code,
                    quantity_base, signed_quantity_effect, occurred_at, reason,
                    recorded_by_profile_id)
                VALUES (?, ?, ?, ?, 'ISSUE', 'KG', 1, -1, clock_timestamp(),
                        'Historical usage fixture', ?)
                """, UUID.fromString("51000000-0000-0000-0000-000000000006"),
                TENANT_ID, ASSIGNED_ID, MATERIAL_ID, PROFILE_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO stock_balances (
                    id, tenant_id, warehouse_id, material_id, unit_code,
                    quantity_on_hand, inventory_value_vnd)
                VALUES (?, ?, ?, ?, 'KG', 12.5, 250000)
                """,
                UUID.fromString("51000000-0000-0000-0000-000000000005"),
                TENANT_ID, ASSIGNED_ID, MATERIAL_ID);
    }

    private void revokeAssignment(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                UPDATE user_warehouse_assignments
                   SET revoked_at = CURRENT_TIMESTAMP, version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND warehouse_id = ? AND revoked_at IS NULL
                """, TENANT_ID, ASSIGNED_ID);
    }

    private void clearStock(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                UPDATE stock_balances
                   SET quantity_on_hand = 0, inventory_value_vnd = 0,
                       version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND warehouse_id = ?
                """, TENANT_ID, ASSIGNED_ID);
    }

    private void authenticateTenantAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
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
