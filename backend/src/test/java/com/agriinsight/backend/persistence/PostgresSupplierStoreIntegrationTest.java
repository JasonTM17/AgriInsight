package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.SupplierCommands;
import com.agriinsight.backend.inventory.application.SupplierQuery;
import com.agriinsight.backend.inventory.domain.Supplier;
import com.agriinsight.backend.inventory.infrastructure.PostgresSupplierStore;
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
class PostgresSupplierStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID SUPPLIER_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final UUID UNUSED_ID = UUID.fromString("54000000-0000-0000-0000-000000000002");
    private static final UUID WAREHOUSE_ID = UUID.fromString("54000000-0000-0000-0000-000000000003");
    private static final UUID MATERIAL_ID = UUID.fromString("54000000-0000-0000-0000-000000000004");
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
    void safeCatalogScopeVersionedMutationAndReferenceLifecycleFailClosed() throws Throwable {
        authenticateInventoryManager();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresSupplierStore store = new PostgresSupplierStore(harness.jdbcTemplate());
            ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);
            ScopeContext listScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.empty());
            ScopeContext targetedScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.of(WAREHOUSE_ID));

            harness.withinTenant(() -> {
                store.create(tenantScope, supplier(SUPPLIER_ID, "SUP-A", "AAA Supplier"));
                store.create(tenantScope, supplier(UNUSED_ID, "SUP-B", "BBB Supplier"));

                assertThat(store.findAll(listScope, query(25)).items()).isEmpty();
                insertInventoryAnchors(harness);
                assertThat(store.findAll(listScope, query(1))).satisfies(page -> {
                    assertThat(page.items()).extracting(item -> item.id())
                            .containsExactly(SUPPLIER_ID);
                    assertThat(page.hasMore()).isTrue();
                });
                assertThat(store.findById(listScope, UNUSED_ID)).isPresent();
                assertThatThrownBy(() -> store.findById(targetedScope, SUPPLIER_ID))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("warehouse-list");

                assertThat(store.update(tenantScope, SUPPLIER_ID, 0, updateName("Supplier Updated")))
                        .get().satisfies(item -> {
                            assertThat(item.displayName()).isEqualTo("Supplier Updated");
                            assertThat(item.version()).isEqualTo(1L);
                        });
                assertThat(store.update(
                        tenantScope, SUPPLIER_ID, 1, updateName("Supplier Updated"))).isEmpty();
                assertThat(store.update(tenantScope, SUPPLIER_ID, 0, updateName("Stale"))).isEmpty();

                insertReceiptReference(harness);
                assertThat(store.hasReferences(tenantScope, SUPPLIER_ID)).isTrue();
                assertThat(store.updateActive(tenantScope, SUPPLIER_ID, 1, false)).isEmpty();
                assertThat(store.updateActive(tenantScope, UNUSED_ID, 0, false))
                        .get().satisfies(item -> {
                            assertThat(item.active()).isFalse();
                            assertThat(item.version()).isEqualTo(1L);
                        });
                return null;
            });
        }
    }

    private Supplier supplier(UUID id, String code, String name) {
        return new Supplier(id, TENANT_ID, code, name);
    }

    private SupplierQuery query(int limit) {
        return new SupplierQuery(limit, 0, Optional.empty(), Optional.empty());
    }

    private SupplierCommands.Update updateName(String name) {
        return new SupplierCommands.Update(Optional.empty(), Optional.of(name), 0, AUDIT);
    }

    private void insertInventoryAnchors(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO warehouses (id, tenant_id, code, display_name)
                VALUES (?, ?, 'WH-SUPPLIER', 'Supplier Warehouse')
                """, WAREHOUSE_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO materials (id, tenant_id, code, display_name, base_unit)
                VALUES (?, ?, 'MAT-SUPPLIER', 'Supplier Material', 'KG')
                """, MATERIAL_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO user_warehouse_assignments (
                    id, tenant_id, user_profile_id, warehouse_id)
                VALUES (?, ?, ?, ?)
                """, UUID.fromString("54000000-0000-0000-0000-000000000005"),
                TENANT_ID, PROFILE_ID, WAREHOUSE_ID);
    }

    private void insertReceiptReference(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO inventory_transactions (
                    id, tenant_id, warehouse_id, material_id, kind, unit_code,
                    quantity_base, signed_quantity_effect, unit_cost_vnd,
                    procurement_effect_vnd, supplier_id, batch_code, expiry_date,
                    occurred_at, recorded_by_profile_id)
                VALUES (?, ?, ?, ?, 'RECEIPT', 'KG', 1, 1, 100, 100, ?,
                        'BATCH-A', DATE '2027-12-31', CURRENT_TIMESTAMP, ?)
                """, UUID.fromString("54000000-0000-0000-0000-000000000006"),
                TENANT_ID, WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID, PROFILE_ID);
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
