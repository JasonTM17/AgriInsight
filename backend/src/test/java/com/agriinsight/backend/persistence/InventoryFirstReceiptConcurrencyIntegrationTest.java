package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.infrastructure.PostgresInventoryTransactionStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class InventoryFirstReceiptConcurrencyIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID WAREHOUSE_ID =
            UUID.fromString("59200000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID =
            UUID.fromString("59200000-0000-0000-0000-000000000002");
    private static final UUID SUPPLIER_ID =
            UUID.fromString("59200000-0000-0000-0000-000000000003");
    private static final ScopeContext SCOPE = ScopeContext.domain(
            new TestPrincipal(), ScopeContext.Type.WAREHOUSE, Optional.of(WAREHOUSE_ID));
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

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
    void concurrentFirstReceiptsConvergeOnOneBalanceWithoutLostQuantity() throws Throwable {
        authenticate();
        seedCatalog();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> postReceipt("FIRST", "3", "10", ready, start));
            Future<Boolean> second = executor.submit(
                    () -> postReceipt("SECOND", "4", "20", ready, start));
            ready.await();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsOnly(true);
        } finally {
            executor.shutdownNow();
        }
        assertProjection();
    }

    private void seedCatalog() throws Throwable {
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO warehouses (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'WH-FIRST', 'First Receipt Warehouse')
                        """, WAREHOUSE_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO materials (id, tenant_id, code, display_name, base_unit)
                        VALUES (?, ?, 'MAT-FIRST', 'First Receipt Material', 'KG')
                        """, MATERIAL_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO suppliers (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'SUP-FIRST', 'First Receipt Supplier')
                        """, SUPPLIER_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO user_warehouse_assignments (
                            id, tenant_id, user_profile_id, warehouse_id)
                        VALUES (?, ?, ?, ?)
                        """, UUID.fromString("59200000-0000-0000-0000-000000000004"),
                        TENANT_ID, PROFILE_ID, WAREHOUSE_ID);
                return null;
            });
        }
    }

    private boolean postReceipt(
            String batch,
            String quantity,
            String cost,
            CountDownLatch ready,
            CountDownLatch start) {
        authenticate();
        ready.countDown();
        try {
            start.await();
            try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                    POSTGRESQL, "agriinsight")) {
                var store = new PostgresInventoryTransactionStore(harness.jdbcTemplate());
                harness.withinTenant(() -> store.post(SCOPE, UUID.randomUUID(),
                        new InventoryTransactionCommands.Receipt(
                                WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID,
                                new BigDecimal(quantity), new BigDecimal(cost), batch,
                                LocalDate.parse("2028-12-31"),
                                Instant.parse("2027-01-01T00:00:00Z"),
                                Optional.empty(), AUDIT)));
                return true;
            }
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertProjection() throws Throwable {
        authenticate();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            harness.withinTenant(() -> {
                var values = harness.jdbcTemplate().queryForMap("""
                        SELECT quantity_on_hand, inventory_value_vnd FROM stock_balances
                         WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                        """, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
                assertThat((BigDecimal) values.get("quantity_on_hand"))
                        .isEqualByComparingTo("7.0000");
                assertThat((BigDecimal) values.get("inventory_value_vnd"))
                        .isEqualByComparingTo("110.00");
                Integer lots = harness.jdbcTemplate().queryForObject("""
                        SELECT count(*) FROM stock_lots
                         WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                        """, Integer.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
                assertThat(lots).isEqualTo(2);
                return null;
            });
        }
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new TestPrincipal(), null,
                        List.of(new SimpleGrantedAuthority(Role.INVENTORY_MANAGER.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
