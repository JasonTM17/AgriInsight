package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryReversalLots {

    private final JdbcTemplate jdbcTemplate;

    PostgresInventoryReversalLots(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    ReversalLotPlan lockPlan(
            ScopeContext scope,
            InventoryTransactionRecord original,
            BigDecimal quantity) {
        if (original.kind() == InventoryTransactionKind.RECEIPT) {
            return lockReceiptPlan(scope, original, quantity);
        }
        return lockIssuePlan(scope, original, quantity);
    }

    void apply(
            ScopeContext scope,
            InventoryTransactionRecord reversal,
            ReversalLotPlan plan) {
        BigDecimal direction = plan.receiptReversal()
                ? BigDecimal.ONE.negate()
                : BigDecimal.ONE;
        for (LotQuantity lot : plan.lots()) {
            updateLot(scope, lot.id(), lot.quantity().multiply(direction));
            if (!plan.receiptReversal()) {
                insertAllocation(scope, reversal, lot);
            }
        }
    }

    private ReversalLotPlan lockReceiptPlan(
            ScopeContext scope,
            InventoryTransactionRecord original,
            BigDecimal quantity) {
        List<LotQuantity> lots = jdbcTemplate.query("""
                SELECT lot.id, lot.available_quantity AS quantity
                  FROM stock_lots AS lot
                 WHERE lot.tenant_id = ? AND lot.original_receipt_id = ?
                 FOR UPDATE
                """,
                (result, rowNumber) -> new LotQuantity(
                        result.getObject("id", UUID.class),
                        result.getBigDecimal("quantity")),
                scope.tenantId(), original.id());
        if (lots.size() != 1 || lots.getFirst().quantity().compareTo(quantity) < 0) {
            throw new ResourceStateConflictException(
                    "Receipt stock has already been consumed");
        }
        return new ReversalLotPlan(true, List.of(
                new LotQuantity(lots.getFirst().id(), quantity)));
    }

    private ReversalLotPlan lockIssuePlan(
            ScopeContext scope,
            InventoryTransactionRecord original,
            BigDecimal quantity) {
        List<LotQuantity> available = jdbcTemplate.query("""
                SELECT lot.id,
                       original_allocation.quantity_base
                           - COALESCE(reversed.quantity_base, 0) AS quantity
                  FROM inventory_transaction_lot_allocations AS original_allocation
                  JOIN stock_lots AS lot
                    ON lot.tenant_id = original_allocation.tenant_id
                   AND lot.id = original_allocation.stock_lot_id
                  LEFT JOIN (
                        SELECT reversal_allocation.stock_lot_id,
                               SUM(reversal_allocation.quantity_base) AS quantity_base
                          FROM inventory_transactions AS reversal
                          JOIN inventory_transaction_lot_allocations AS reversal_allocation
                            ON reversal_allocation.tenant_id = reversal.tenant_id
                           AND reversal_allocation.transaction_id = reversal.id
                         WHERE reversal.tenant_id = ? AND reversal.reversal_of = ?
                         GROUP BY reversal_allocation.stock_lot_id
                  ) AS reversed ON reversed.stock_lot_id = lot.id
                 WHERE original_allocation.tenant_id = ?
                   AND original_allocation.transaction_id = ?
                   AND original_allocation.quantity_base
                       - COALESCE(reversed.quantity_base, 0) > 0
                 ORDER BY lot.expiry_date, lot.received_at, lot.id
                 FOR UPDATE OF lot
                """,
                (result, rowNumber) -> new LotQuantity(
                        result.getObject("id", UUID.class),
                        result.getBigDecimal("quantity")),
                scope.tenantId(), original.id(), scope.tenantId(), original.id());
        List<LotQuantity> plan = new ArrayList<>();
        BigDecimal remaining = quantity;
        for (LotQuantity lot : available) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal restored = remaining.min(lot.quantity());
            plan.add(new LotQuantity(lot.id(), restored));
            remaining = remaining.subtract(restored);
        }
        if (remaining.signum() != 0) {
            throw new IllegalStateException("Issue allocations do not match reversible quantity");
        }
        return new ReversalLotPlan(false, List.copyOf(plan));
    }

    private void updateLot(ScopeContext scope, UUID lotId, BigDecimal delta) {
        int updated = jdbcTemplate.update("""
                UPDATE stock_lots AS lot
                   SET available_quantity = lot.available_quantity + ?,
                       version = lot.version + 1,
                       updated_at = GREATEST(lot.created_at, clock_timestamp())
                 WHERE lot.tenant_id = ? AND lot.id = ?
                   AND lot.available_quantity + ? BETWEEN 0 AND lot.received_quantity
                """, delta, scope.tenantId(), lotId, delta);
        if (updated != 1) {
            throw new IllegalStateException("Locked reversal stock lot quantity changed");
        }
    }

    private void insertAllocation(
            ScopeContext scope,
            InventoryTransactionRecord reversal,
            LotQuantity lot) {
        jdbcTemplate.update("""
                INSERT INTO inventory_transaction_lot_allocations (
                    id, tenant_id, transaction_id, stock_lot_id,
                    warehouse_id, material_id, quantity_base)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), scope.tenantId(), reversal.id(), lot.id(),
                reversal.warehouseId(), reversal.materialId(), lot.quantity());
    }

    record ReversalLotPlan(boolean receiptReversal, List<LotQuantity> lots) {
    }

    private record LotQuantity(UUID id, BigDecimal quantity) {
    }
}
