package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.InventoryLedgerAssertions.assertAllocations;
import static com.agriinsight.backend.persistence.support.InventoryLedgerAssertions.assertBalance;
import static com.agriinsight.backend.persistence.support.InventoryLedgerAssertions.assertExplicitLotSelection;
import static com.agriinsight.backend.persistence.support.InventoryLedgerAssertions.assertReadModels;
import static com.agriinsight.backend.persistence.support.InventoryLedgerAssertions.assertReconciliationDetectsDrift;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresInventoryTransactionStoreIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID WAREHOUSE_ID =
            UUID.fromString("59000000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID =
            UUID.fromString("59000000-0000-0000-0000-000000000002");
    private static final UUID SUPPLIER_ID =
            UUID.fromString("59000000-0000-0000-0000-000000000003");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());
    private static final ScopeContext SCOPE = ScopeContext.domain(
            new TestPrincipal(), ScopeContext.Type.WAREHOUSE, Optional.of(WAREHOUSE_ID));

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new TestPrincipal(), null,
                        List.of(new SimpleGrantedAuthority(Role.INVENTORY_MANAGER.authority()))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void receiptIssueFefoAndReversalsKeepLedgerLotsAndBalanceConsistent() throws Throwable {
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresInventoryTransactionStore store =
                    new PostgresInventoryTransactionStore(harness.jdbcTemplate());
            createCatalog(harness);

            InventoryTransactionRecord late = post(harness, store, receipt(
                    "LATE", "5", "20", "2027-12-31", "2027-01-01T08:00:00Z"));
            InventoryTransactionRecord early = post(harness, store, receipt(
                    "EARLY", "4", "10", "2027-06-30", "2027-01-01T08:01:00Z"));
            post(harness, store, receipt(
                    "EXPIRED", "3", "30", "2026-01-31", "2025-12-01T08:02:00Z"));
            assertThatThrownBy(() -> post(harness, store,
                    new InventoryTransactionCommands.Issue(
                            WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("1"), Optional.empty(),
                            Instant.parse("2026-12-31T23:59:59Z"), "Backdated issue",
                            Optional.empty(), AUDIT)))
                    .isInstanceOf(ResourceStateConflictException.class)
                    .hasMessage("Insufficient eligible stock");
            assertExplicitLotSelection(
                    harness, store, SCOPE, WAREHOUSE_ID, MATERIAL_ID, AUDIT);

            InventoryTransactionRecord issue = post(harness, store,
                    new InventoryTransactionCommands.Issue(
                            WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("6"), Optional.empty(),
                            Instant.parse("2027-02-01T08:00:00Z"), "Field application",
                            Optional.of("ISSUE-001"), AUDIT));

            assertThat(issue.kind()).isEqualTo(InventoryTransactionKind.ISSUE);
            assertAllocations(harness, TENANT_ID, issue.id());
            assertBalance(harness, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID, "6.0000", "150.00");
            assertReadModels(harness, SCOPE, WAREHOUSE_ID, MATERIAL_ID, issue.id());

            assertThatThrownBy(() -> post(harness, store,
                    new InventoryTransactionCommands.Issue(
                            WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("20"), Optional.empty(),
                            Instant.parse("2027-02-01T09:00:00Z"), "Too much",
                            Optional.empty(), AUDIT)))
                    .isInstanceOf(ResourceStateConflictException.class)
                    .hasMessage("Insufficient eligible stock");
            assertBalance(harness, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID, "6.0000", "150.00");

            assertThatThrownBy(() -> reverse(harness, store, early.id(), "1", 0))
                    .isInstanceOf(ResourceStateConflictException.class)
                    .hasMessage("Receipt stock has already been consumed");

            InventoryTransactionRecord issueReversal = reverse(
                    harness, store, issue.id(), "2", 0);
            assertThat(issueReversal.signedQuantityEffect()).isEqualByComparingTo("2.0000");
            assertBalance(harness, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID, "8.0000", "170.00");
            reverse(harness, store, issue.id(), "4", 1);
            assertBalance(harness, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID, "12.0000", "230.00");

            InventoryTransactionRecord receiptReversal = reverse(
                    harness, store, early.id(), "1", 0);
            assertThat(receiptReversal.procurementEffectVnd()).isEqualByComparingTo("-10.00");
            assertBalance(harness, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID, "11.0000", "220.00");
            assertThatThrownBy(() -> reverse(harness, store, early.id(), "4", 1))
                    .isInstanceOf(ResourceStateConflictException.class)
                    .hasMessage("Reversal quantity exceeds remaining original quantity");
            assertThat(late.version()).isZero();
            assertReconciliationDetectsDrift(
                    harness, SCOPE, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        }
    }

    private void createCatalog(TenantTransactionTestHarness harness) throws Throwable {
        harness.withinTenant(() -> {
            harness.jdbcTemplate().update("""
                    INSERT INTO warehouses (id, tenant_id, code, display_name)
                    VALUES (?, ?, 'WH-LEDGER', 'Ledger Warehouse')
                    """, WAREHOUSE_ID, TENANT_ID);
            harness.jdbcTemplate().update("""
                    INSERT INTO materials (
                        id, tenant_id, code, display_name, base_unit, minimum_stock_quantity)
                    VALUES (?, ?, 'FERT-LEDGER', 'Ledger Fertilizer', 'KG', 10)
                    """, MATERIAL_ID, TENANT_ID);
            harness.jdbcTemplate().update("""
                    INSERT INTO suppliers (id, tenant_id, code, display_name)
                    VALUES (?, ?, 'SUP-LEDGER', 'Ledger Supplier')
                    """, SUPPLIER_ID, TENANT_ID);
            harness.jdbcTemplate().update("""
                    INSERT INTO user_warehouse_assignments (
                        id, tenant_id, user_profile_id, warehouse_id)
                    VALUES (?, ?, ?, ?)
                    """, UUID.fromString("59000000-0000-0000-0000-000000000004"),
                    TENANT_ID, PROFILE_ID, WAREHOUSE_ID);
            return null;
        });
    }

    private InventoryTransactionCommands.Receipt receipt(
            String batch, String quantity, String cost, String expiry, String occurredAt) {
        return new InventoryTransactionCommands.Receipt(
                WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID, new BigDecimal(quantity),
                new BigDecimal(cost), batch, LocalDate.parse(expiry), Instant.parse(occurredAt),
                Optional.empty(), AUDIT);
    }

    private InventoryTransactionRecord post(
            TenantTransactionTestHarness harness,
            PostgresInventoryTransactionStore store,
            InventoryTransactionCommands.Posting command) throws Throwable {
        return harness.withinTenant(() -> store.post(SCOPE, UUID.randomUUID(), command));
    }

    private InventoryTransactionRecord reverse(
            TenantTransactionTestHarness harness,
            PostgresInventoryTransactionStore store,
            UUID originalId,
            String quantity,
            long version) throws Throwable {
        return harness.withinTenant(() -> store.reverse(
                SCOPE, originalId, UUID.randomUUID(), new InventoryTransactionCommands.Reversal(
                        new BigDecimal(quantity), "Correction", version, AUDIT)));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
