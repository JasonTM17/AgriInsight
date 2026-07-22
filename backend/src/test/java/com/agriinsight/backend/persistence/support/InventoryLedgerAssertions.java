package com.agriinsight.backend.persistence.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
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
}
