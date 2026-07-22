package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.infrastructure.PostgresInventoryTransactionStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
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
class InventoryIssueConcurrencyIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID WAREHOUSE_ID =
            UUID.fromString("59100000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID =
            UUID.fromString("59100000-0000-0000-0000-000000000002");
    private static final UUID SUPPLIER_ID =
            UUID.fromString("59100000-0000-0000-0000-000000000003");
    private static final ScopeContext SCOPE = ScopeContext.tenant(new TestPrincipal());
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
    void concurrentIssuesCannotOversellOneBalance() throws Throwable {
        authenticate();
        seedReceipt();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> issueOnce(ready, start));
            Future<Boolean> second = executor.submit(() -> issueOnce(ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        assertBalanceIsOne();
    }

    private void seedReceipt() throws Throwable {
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO warehouses (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'WH-CONCURRENT', 'Concurrent Warehouse')
                        """, WAREHOUSE_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO materials (id, tenant_id, code, display_name, base_unit)
                        VALUES (?, ?, 'MAT-CONCURRENT', 'Concurrent Material', 'KG')
                        """, MATERIAL_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO suppliers (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'SUP-CONCURRENT', 'Concurrent Supplier')
                        """, SUPPLIER_ID, TENANT_ID);
                return null;
            });
            var store = new PostgresInventoryTransactionStore(harness.jdbcTemplate());
            harness.withinTenant(() -> store.post(SCOPE, UUID.randomUUID(),
                    new InventoryTransactionCommands.Receipt(
                            WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID, new BigDecimal("5"),
                            new BigDecimal("10"), "CONCURRENT", LocalDate.parse("2028-12-31"),
                            Instant.parse("2027-01-01T00:00:00Z"), Optional.empty(), AUDIT)));
        }
    }

    private boolean issueOnce(CountDownLatch ready, CountDownLatch start) {
        authenticate();
        ready.countDown();
        try {
            start.await();
            try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                    POSTGRESQL, "agriinsight")) {
                var store = new PostgresInventoryTransactionStore(harness.jdbcTemplate());
                harness.withinTenant(() -> store.post(SCOPE, UUID.randomUUID(),
                        new InventoryTransactionCommands.Issue(
                                WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("4"), Optional.empty(),
                                Instant.parse("2027-02-01T00:00:00Z"), "Concurrent issue",
                                Optional.empty(), AUDIT)));
                return true;
            }
        } catch (ResourceStateConflictException expected) {
            return false;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertBalanceIsOne() throws Throwable {
        authenticate();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            harness.withinTenant(() -> {
                BigDecimal quantity = harness.jdbcTemplate().queryForObject("""
                        SELECT quantity_on_hand FROM stock_balances
                         WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                        """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
                assertThat(quantity).isEqualByComparingTo("1.0000");
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
