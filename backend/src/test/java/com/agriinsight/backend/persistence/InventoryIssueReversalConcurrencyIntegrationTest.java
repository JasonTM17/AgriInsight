package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.infrastructure.PostgresInventoryReconciliationStore;
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
class InventoryIssueReversalConcurrencyIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID WAREHOUSE_ID =
            UUID.fromString("59300000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID =
            UUID.fromString("59300000-0000-0000-0000-000000000002");
    private static final UUID SUPPLIER_ID =
            UUID.fromString("59300000-0000-0000-0000-000000000003");
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
    void concurrentIssueAndReversalSerializeWithoutProjectionDrift() throws Throwable {
        authenticate();
        UUID originalIssueId = seedReceiptAndIssue();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        boolean issueSucceeded;
        try {
            Future<Boolean> reversal = executor.submit(
                    () -> reverse(originalIssueId, ready, start));
            Future<Boolean> issue = executor.submit(() -> issue(ready, start));
            ready.await();
            start.countDown();

            assertThat(reversal.get()).isTrue();
            issueSucceeded = issue.get();
        } finally {
            executor.shutdownNow();
        }
        assertProjection(issueSucceeded ? "1.0000" : "5.0000");
    }

    private UUID seedReceiptAndIssue() throws Throwable {
        try (TenantTransactionTestHarness harness = runtimeHarness()) {
            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO warehouses (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'WH-REV-RACE', 'Reversal Race Warehouse')
                        """, WAREHOUSE_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO materials (id, tenant_id, code, display_name, base_unit)
                        VALUES (?, ?, 'MAT-REV-RACE', 'Reversal Race Material', 'KG')
                        """, MATERIAL_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO suppliers (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'SUP-REV-RACE', 'Reversal Race Supplier')
                        """, SUPPLIER_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO user_warehouse_assignments (
                            id, tenant_id, user_profile_id, warehouse_id)
                        VALUES (?, ?, ?, ?)
                        """, UUID.fromString("59300000-0000-0000-0000-000000000004"),
                        TENANT_ID, PROFILE_ID, WAREHOUSE_ID);
                return null;
            });
            var store = new PostgresInventoryTransactionStore(harness.jdbcTemplate());
            harness.withinTenant(() -> store.post(SCOPE, UUID.randomUUID(),
                    new InventoryTransactionCommands.Receipt(
                            WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID, new BigDecimal("5"),
                            new BigDecimal("10"), "REV-RACE", LocalDate.parse("2028-12-31"),
                            Instant.parse("2027-01-01T00:00:00Z"), Optional.empty(), AUDIT)));
            return harness.withinTenant(() -> store.post(SCOPE, UUID.randomUUID(), issueCommand()))
                    .id();
        }
    }

    private boolean reverse(UUID issueId, CountDownLatch ready, CountDownLatch start) {
        return concurrently(ready, start, store -> {
            store.reverse(SCOPE, issueId, UUID.randomUUID(),
                    new InventoryTransactionCommands.Reversal(
                            new BigDecimal("4"), "Concurrent correction", 0, AUDIT));
            return true;
        });
    }

    private boolean issue(CountDownLatch ready, CountDownLatch start) {
        try {
            return concurrently(ready, start, store -> {
                store.post(SCOPE, UUID.randomUUID(), issueCommand());
                return true;
            });
        } catch (IllegalStateException failure) {
            if (failure.getCause() instanceof ResourceStateConflictException) {
                return false;
            }
            throw failure;
        }
    }

    private boolean concurrently(
            CountDownLatch ready,
            CountDownLatch start,
            StoreOperation operation) {
        authenticate();
        ready.countDown();
        try {
            start.await();
            try (TenantTransactionTestHarness harness = runtimeHarness()) {
                var store = new PostgresInventoryTransactionStore(harness.jdbcTemplate());
                return harness.withinTenant(() -> operation.run(store));
            }
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertProjection(String expectedQuantity) throws Throwable {
        authenticate();
        try (TenantTransactionTestHarness harness = runtimeHarness()) {
            harness.withinTenant(() -> {
                BigDecimal quantity = harness.jdbcTemplate().queryForObject("""
                        SELECT quantity_on_hand FROM stock_balances
                         WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                        """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
                assertThat(quantity).isEqualByComparingTo(expectedQuantity);
                assertThat(quantity).isNotNegative();
                assertThat(new PostgresInventoryReconciliationStore(
                        harness.jdbcTemplate()).reconcile(SCOPE).consistent()).isTrue();
                return null;
            });
        }
    }

    private InventoryTransactionCommands.Issue issueCommand() {
        return new InventoryTransactionCommands.Issue(
                WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("4"), Optional.empty(),
                Instant.parse("2027-02-01T00:00:00Z"), "Concurrent issue",
                Optional.empty(), AUDIT);
    }

    private TenantTransactionTestHarness runtimeHarness() {
        return TenantTransactionTestHarness.runtime(POSTGRESQL, "agriinsight");
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new TestPrincipal(), null,
                        List.of(new SimpleGrantedAuthority(Role.INVENTORY_MANAGER.authority()))));
    }

    @FunctionalInterface
    private interface StoreOperation {
        boolean run(PostgresInventoryTransactionStore store) throws Throwable;
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
