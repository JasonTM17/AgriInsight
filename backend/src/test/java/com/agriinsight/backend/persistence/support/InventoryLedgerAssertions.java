package com.agriinsight.backend.persistence.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionQuery;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.application.StockBalanceQuery;
import com.agriinsight.backend.inventory.application.StockLotQuery;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.inventory.infrastructure.PostgresInventoryReadStore;
import com.agriinsight.backend.inventory.infrastructure.PostgresInventoryReconciliationStore;
import com.agriinsight.backend.inventory.infrastructure.PostgresInventoryTransactionStore;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InventoryLedgerAssertions {

    private InventoryLedgerAssertions() {
    }

    public static void assertAllocations(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID transactionId) throws Throwable {
        harness.withinTenant(() -> {
            List<String> allocations = harness.jdbcTemplate().query("""
                    SELECT lot.batch_code || ':' || allocation.quantity_base
                      FROM inventory_transaction_lot_allocations AS allocation
                      JOIN stock_lots AS lot ON lot.tenant_id = allocation.tenant_id
                       AND lot.id = allocation.stock_lot_id
                     WHERE allocation.tenant_id = ? AND allocation.transaction_id = ?
                     ORDER BY lot.expiry_date
                    """, (result, rowNumber) -> result.getString(1), tenantId, transactionId);
            assertThat(allocations).containsExactly("EARLY:4.0000", "LATE:2.0000");
            return null;
        });
    }

    public static void assertBalance(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            UUID materialId,
            String quantity,
            String value) throws Throwable {
        harness.withinTenant(() -> {
            var values = harness.jdbcTemplate().queryForMap("""
                    SELECT quantity_on_hand, inventory_value_vnd FROM stock_balances
                     WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                    """, tenantId, warehouseId, materialId);
            assertThat((BigDecimal) values.get("quantity_on_hand"))
                    .isEqualByComparingTo(quantity);
            assertThat((BigDecimal) values.get("inventory_value_vnd"))
                    .isEqualByComparingTo(value);
            return null;
        });
    }

    public static void assertReadModels(
            TenantTransactionTestHarness harness,
            ScopeContext scope,
            UUID warehouseId,
            UUID materialId,
            UUID issueId) throws Throwable {
        harness.withinTenant(() -> {
            var reads = new PostgresInventoryReadStore(harness.jdbcTemplate());
            var transactions = reads.findTransactions(scope, new InventoryTransactionQuery(
                    10, 0, Optional.of(warehouseId), Optional.of(materialId),
                    Optional.of(InventoryTransactionKind.ISSUE),
                    Optional.of(Instant.parse("2027-02-01T08:00:00Z")),
                    Optional.of(Instant.parse("2027-02-01T08:00:00Z"))));
            assertThat(transactions.items()).singleElement()
                    .extracting(item -> item.id()).isEqualTo(issueId);
            assertThat(reads.findTransaction(scope, issueId)).isPresent();

            var balances = reads.findBalances(scope, new StockBalanceQuery(
                    10, 0, Optional.of(warehouseId), Optional.of(materialId),
                    Optional.of(true)));
            assertThat(balances.items()).singleElement().satisfies(balance -> {
                assertThat(balance.quantityOnHand()).isEqualByComparingTo("6.0000");
                assertThat(balance.inventoryValueVnd()).isEqualByComparingTo("150.00");
                assertThat(balance.lowStock()).isTrue();
            });

            var lots = reads.findLots(scope, new StockLotQuery(
                    10, 0, Optional.of(warehouseId), Optional.of(materialId),
                    Optional.empty(), false));
            assertThat(lots.items()).extracting(item -> item.batchCode())
                    .containsExactly("EXPIRED", "LATE");
            assertThat(lots.items()).filteredOn(item -> item.expired())
                    .extracting(item -> item.batchCode()).containsExactly("EXPIRED");
            var expired = reads.findLots(scope, new StockLotQuery(
                    10, 0, Optional.empty(), Optional.empty(),
                    Optional.of(LocalDate.parse("2026-02-01")), false));
            assertThat(expired.items()).extracting(item -> item.batchCode())
                    .containsExactly("EXPIRED");
            return null;
        });
    }

    public static void assertReconciliationDetectsDrift(
            TenantTransactionTestHarness harness,
            ScopeContext scope,
            UUID tenantId,
            UUID warehouseId,
            UUID materialId) throws Throwable {
        harness.withinTenant(() -> {
            var store = new PostgresInventoryReconciliationStore(harness.jdbcTemplate());
            var report = store.reconcile(scope);
            assertThat(report.consistent()).isTrue();
            assertThat(report.checkedLotCount()).isEqualTo(3);
            assertThat(report.checkedBalanceCount()).isEqualTo(1);
            return null;
        });
        harness.withinTenant(() -> {
            harness.jdbcTemplate().update("""
                    UPDATE stock_balances SET quantity_on_hand = 999
                     WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                    """, tenantId, warehouseId, materialId);
            harness.jdbcTemplate().update("""
                    UPDATE stock_lots SET available_quantity = available_quantity - 1
                     WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                       AND batch_code = 'EARLY'
                    """, tenantId, warehouseId, materialId);
            var report = new PostgresInventoryReconciliationStore(
                    harness.jdbcTemplate()).reconcile(scope);
            assertThat(report.lotDriftCount()).isEqualTo(1);
            assertThat(report.balanceDriftCount()).isEqualTo(1);
            return null;
        });
    }

    public static void assertExplicitLotSelection(
            TenantTransactionTestHarness harness,
            PostgresInventoryTransactionStore store,
            ScopeContext scope,
            UUID warehouseId,
            UUID materialId,
            TenantAuditMetadata audit) throws Throwable {
        UUID lateLotId = lotId(
                harness, scope.tenantId(), warehouseId, materialId, "LATE");
        UUID explicitIssueId = UUID.randomUUID();
        harness.withinTenant(() -> store.post(scope, explicitIssueId,
                new InventoryTransactionCommands.Issue(
                        warehouseId, materialId, BigDecimal.ONE, Optional.of(lateLotId),
                        Instant.parse("2027-02-01T07:00:00Z"), "Explicit batch use",
                        Optional.empty(), audit)));
        harness.withinTenant(() -> {
            List<String> batches = harness.jdbcTemplate().query("""
                    SELECT lot.batch_code FROM inventory_transaction_lot_allocations allocation
                    JOIN stock_lots lot ON lot.tenant_id = allocation.tenant_id
                     AND lot.id = allocation.stock_lot_id
                    WHERE allocation.tenant_id = ? AND allocation.transaction_id = ?
                    """, (row, rowNumber) -> row.getString(1), scope.tenantId(), explicitIssueId);
            assertThat(batches).containsExactly("LATE");
            return null;
        });
        harness.withinTenant(() -> store.reverse(
                scope, explicitIssueId, UUID.randomUUID(),
                new InventoryTransactionCommands.Reversal(
                        BigDecimal.ONE, "Restore explicit issue", 0, audit)));

        UUID expiredLotId = lotId(
                harness, scope.tenantId(), warehouseId, materialId, "EXPIRED");
        assertThatThrownBy(() -> harness.withinTenant(() -> store.post(
                scope, UUID.randomUUID(), new InventoryTransactionCommands.Issue(
                        warehouseId, materialId, BigDecimal.ONE, Optional.of(expiredLotId),
                        Instant.parse("2027-02-01T07:30:00Z"), "Expired batch use",
                        Optional.empty(), audit))))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessage("Insufficient eligible stock");
    }

    private static UUID lotId(
            TenantTransactionTestHarness harness,
            UUID tenantId,
            UUID warehouseId,
            UUID materialId,
            String batch)
            throws Throwable {
        return harness.withinTenant(() -> harness.jdbcTemplate().queryForObject("""
                SELECT id FROM stock_lots
                 WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
                   AND batch_code = ?
                """, UUID.class, tenantId, warehouseId, materialId, batch));
    }
}
