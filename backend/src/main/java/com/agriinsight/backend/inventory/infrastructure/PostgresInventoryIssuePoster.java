package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryIssuePoster {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresInventoryLedger ledger;
    private final PostgresInventoryBalanceProjection balances;

    PostgresInventoryIssuePoster(
            JdbcTemplate jdbcTemplate,
            PostgresInventoryLedger ledger,
            PostgresInventoryBalanceProjection balances) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.ledger = Objects.requireNonNull(ledger, "ledger is required");
        this.balances = Objects.requireNonNull(balances, "balances is required");
    }

    InventoryTransactionRecord post(
            ScopeContext scope,
            UUID transactionId,
            CanonicalUnit unit,
            InventoryTransactionCommands.Issue command) {
        balances.lock(scope, command.warehouseId(), command.materialId(), unit);
        List<LotState> lots = lockEligibleLots(scope, command);
        requireAvailable(lots, command.quantityBase());
        InventoryTransactionRecord issue = ledger.insert(new InventoryTransactionRecord(
                transactionId,
                scope.tenantId(),
                command.warehouseId(),
                command.materialId(),
                InventoryTransactionKind.ISSUE,
                unit,
                command.quantityBase(),
                command.quantityBase().negate(),
                Optional.empty(),
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                command.occurredAt(),
                Optional.of(command.reason()),
                command.referenceCode(),
                Optional.empty(),
                scope.profileId(),
                0));
        allocate(scope, issue, lots);
        balances.recompute(scope, command.warehouseId(), command.materialId());
        return issue;
    }

    private List<LotState> lockEligibleLots(
            ScopeContext scope, InventoryTransactionCommands.Issue command) {
        StringBuilder sql = new StringBuilder("""
                SELECT lot.id, lot.available_quantity, lot.unit_code
                  FROM stock_lots AS lot
                 WHERE lot.tenant_id = ?
                   AND lot.warehouse_id = ?
                   AND lot.material_id = ?
                   AND lot.available_quantity > 0
                   AND lot.expiry_date >= ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                scope.tenantId(), command.warehouseId(), command.materialId(),
                command.occurredAt().atZone(ZoneOffset.UTC).toLocalDate()));
        command.stockLotId().ifPresent(lotId -> {
            sql.append(" AND lot.id = ?");
            parameters.add(lotId);
        });
        sql.append(" ORDER BY lot.expiry_date, lot.received_at, lot.id FOR UPDATE");
        return jdbcTemplate.query(
                sql.toString(),
                (result, rowNumber) -> new LotState(
                        result.getObject("id", UUID.class),
                        result.getBigDecimal("available_quantity"),
                        CanonicalUnit.valueOf(result.getString("unit_code"))),
                parameters.toArray());
    }

    private void requireAvailable(List<LotState> lots, BigDecimal requested) {
        BigDecimal available = lots.stream()
                .map(LotState::availableQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(requested) < 0) {
            throw new ResourceStateConflictException("Insufficient eligible stock");
        }
    }

    private void allocate(
            ScopeContext scope,
            InventoryTransactionRecord issue,
            List<LotState> lots) {
        BigDecimal remaining = issue.quantityBase();
        for (LotState lot : lots) {
            if (remaining.signum() == 0) {
                break;
            }
            if (lot.unit() != issue.unit()) {
                throw new IllegalStateException("Stock lot unit does not match material unit");
            }
            BigDecimal quantity = remaining.min(lot.availableQuantity());
            updateLot(scope, lot.id(), quantity.negate());
            insertAllocation(scope, issue, lot.id(), quantity);
            remaining = remaining.subtract(quantity);
        }
        if (remaining.signum() != 0) {
            throw new IllegalStateException("Locked stock lots did not satisfy issue quantity");
        }
    }

    private void updateLot(ScopeContext scope, UUID lotId, BigDecimal delta) {
        int updated = jdbcTemplate.update("""
                UPDATE stock_lots AS lot
                   SET available_quantity = lot.available_quantity + ?,
                       version = lot.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE lot.tenant_id = ? AND lot.id = ?
                   AND lot.available_quantity + ? >= 0
                """, delta, scope.tenantId(), lotId, delta);
        if (updated != 1) {
            throw new IllegalStateException("Locked stock lot quantity changed");
        }
    }

    private void insertAllocation(
            ScopeContext scope,
            InventoryTransactionRecord transaction,
            UUID lotId,
            BigDecimal quantity) {
        jdbcTemplate.update("""
                INSERT INTO inventory_transaction_lot_allocations (
                    id, tenant_id, transaction_id, stock_lot_id,
                    warehouse_id, material_id, quantity_base)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), scope.tenantId(), transaction.id(), lotId,
                transaction.warehouseId(), transaction.materialId(), quantity);
    }

    private record LotState(
            UUID id, BigDecimal availableQuantity, CanonicalUnit unit) {
    }
}
