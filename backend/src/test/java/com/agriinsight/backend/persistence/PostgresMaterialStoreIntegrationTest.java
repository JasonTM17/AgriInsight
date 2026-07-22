package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.MaterialCommands;
import com.agriinsight.backend.inventory.application.MaterialQuery;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import com.agriinsight.backend.inventory.infrastructure.PostgresMaterialStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
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
class PostgresMaterialStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID MATERIAL_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final UUID UNUSED_ID = UUID.fromString("52000000-0000-0000-0000-000000000002");
    private static final UUID WAREHOUSE_ID = UUID.fromString("52000000-0000-0000-0000-000000000003");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());
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
    void catalogScopeVersionedMutationAndLifecycleFailClosed() throws Throwable {
        authenticateInventoryManager();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresMaterialStore store = new PostgresMaterialStore(harness.jdbcTemplate());
            ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);
            ScopeContext listScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.empty());
            ScopeContext targetedScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.of(WAREHOUSE_ID));

            harness.withinTenant(() -> {
                store.create(tenantScope, material(
                        MATERIAL_ID, "FERT-A", "AAA Fertilizer", CanonicalUnit.KG,
                        Optional.of(new BigDecimal("10.5"))));
                store.create(tenantScope, material(
                        UNUSED_ID, "SEED-A", "BBB Seed", CanonicalUnit.PIECE, Optional.empty()));

                assertThat(store.findAll(listScope, query(25)).items()).isEmpty();
                insertWarehouseAssignment(harness);
                assertThat(store.findAll(listScope, query(1))).satisfies(page -> {
                    assertThat(page.items()).extracting(item -> item.id())
                            .containsExactly(MATERIAL_ID);
                    assertThat(page.hasMore()).isTrue();
                });
                assertThat(store.findById(listScope, UNUSED_ID)).isPresent();
                assertThatThrownBy(() -> store.findById(targetedScope, MATERIAL_ID))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("warehouse-list");

                assertThat(store.update(tenantScope, MATERIAL_ID, 0, updateMinimum(Optional.empty())))
                        .get().satisfies(item -> {
                            assertThat(item.minimumStockQuantity()).isEmpty();
                            assertThat(item.version()).isEqualTo(1L);
                        });
                assertThat(store.update(
                        tenantScope, MATERIAL_ID, 1, updateMinimum(Optional.empty()))).isEmpty();
                assertThat(store.update(tenantScope, MATERIAL_ID, 1, updateUnit(CanonicalUnit.LITER)))
                        .get().satisfies(item -> {
                            assertThat(item.baseUnit()).isEqualTo(CanonicalUnit.LITER);
                            assertThat(item.version()).isEqualTo(2L);
                        });
                assertThat(store.update(tenantScope, MATERIAL_ID, 1, updateName("Stale"))).isEmpty();

                insertStockReference(harness);
                assertThat(store.hasReferences(tenantScope, MATERIAL_ID)).isTrue();
                assertThat(store.update(tenantScope, MATERIAL_ID, 2, updateUnit(CanonicalUnit.KG)))
                        .isEmpty();
                assertThat(store.update(tenantScope, MATERIAL_ID, 2, updateName("Fertilizer Updated")))
                        .get().extracting(item -> item.version()).isEqualTo(3L);
                assertThat(store.updateActive(tenantScope, MATERIAL_ID, 3, false)).isEmpty();
                assertThat(store.updateActive(tenantScope, UNUSED_ID, 0, false))
                        .get().satisfies(item -> {
                            assertThat(item.active()).isFalse();
                            assertThat(item.version()).isEqualTo(1L);
                        });
                return null;
            });
        }
    }

    private Material material(
            UUID id, String code, String name, CanonicalUnit unit, Optional<BigDecimal> minimum) {
        return new Material(id, TENANT_ID, code, name, unit, minimum);
    }

    private MaterialQuery query(int limit) {
        return new MaterialQuery(limit, 0, Optional.empty(), Optional.empty());
    }

    private MaterialCommands.Update updateMinimum(Optional<BigDecimal> minimum) {
        return new MaterialCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(minimum), 0, AUDIT);
    }

    private MaterialCommands.Update updateUnit(CanonicalUnit unit) {
        return new MaterialCommands.Update(
                Optional.empty(), Optional.empty(), Optional.of(unit), Optional.empty(), 0, AUDIT);
    }

    private MaterialCommands.Update updateName(String name) {
        return new MaterialCommands.Update(
                Optional.empty(), Optional.of(name), Optional.empty(), Optional.empty(), 0, AUDIT);
    }

    private void insertWarehouseAssignment(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO warehouses (id, tenant_id, code, display_name)
                VALUES (?, ?, 'WH-MATERIAL', 'Material Warehouse')
                """, WAREHOUSE_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO user_warehouse_assignments (
                    id, tenant_id, user_profile_id, warehouse_id)
                VALUES (?, ?, ?, ?)
                """, UUID.fromString("52000000-0000-0000-0000-000000000004"),
                TENANT_ID, PROFILE_ID, WAREHOUSE_ID);
    }

    private void insertStockReference(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO stock_balances (
                    id, tenant_id, warehouse_id, material_id, unit_code,
                    quantity_on_hand, inventory_value_vnd)
                VALUES (?, ?, ?, ?, 'LITER', 0, 0)
                """, UUID.fromString("52000000-0000-0000-0000-000000000005"),
                TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
    }

    private void authenticateInventoryManager() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.INVENTORY_MANAGER.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
