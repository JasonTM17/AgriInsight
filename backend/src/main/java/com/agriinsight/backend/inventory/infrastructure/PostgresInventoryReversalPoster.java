package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.InventoryNumbers;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryReversalPoster {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresInventoryLedger ledger;
    private final PostgresInventoryBalanceProjection balances;
    private final PostgresInventoryReversalLots lots;

    PostgresInventoryReversalPoster(
            JdbcTemplate jdbcTemplate,
            PostgresInventoryLedger ledger,
            PostgresInventoryBalanceProjection balances) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.ledger = Objects.requireNonNull(ledger, "ledger is required");
        this.balances = Objects.requireNonNull(balances, "balances is required");
        this.lots = new PostgresInventoryReversalLots(jdbcTemplate);
    }

    InventoryTransactionRecord post(
            ScopeContext scope,
            UUID originalTransactionId,
            UUID reversalTransactionId,
            InventoryTransactionCommands.Reversal command) {
        InventoryTransactionRecord original = ledger.find(
                        scope, originalTransactionId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory transaction"));
        requireReversible(original, command);
        balances.lock(scope, original.warehouseId(), original.materialId(), original.unit());
        var lotPlan = lots.lockPlan(scope, original, command.quantityBase());
        InventoryTransactionRecord reversal = ledger.insert(reversalRecord(
                scope, reversalTransactionId, original, command));
        lots.apply(scope, reversal, lotPlan);
        ledger.incrementVersion(scope, original.id(), command.expectedVersion());
        balances.recompute(scope, original.warehouseId(), original.materialId());
        return reversal;
    }

    private void requireReversible(
            InventoryTransactionRecord original,
            InventoryTransactionCommands.Reversal command) {
        if (original.kind() == InventoryTransactionKind.REVERSAL) {
            throw new ResourceStateConflictException("A reversal cannot be reversed");
        }
        if (original.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), original.version());
        }
        BigDecimal reversed = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(reversal.quantity_base), 0)
                  FROM inventory_transactions AS reversal
                 WHERE reversal.tenant_id = ? AND reversal.reversal_of = ?
                """, BigDecimal.class, original.tenantId(), original.id());
        BigDecimal remaining = original.quantityBase().subtract(
                Objects.requireNonNull(reversed, "Reversed quantity is required"));
        if (command.quantityBase().compareTo(remaining) > 0) {
            throw new ResourceStateConflictException(
                    "Reversal quantity exceeds remaining original quantity");
        }
    }

    private InventoryTransactionRecord reversalRecord(
            ScopeContext scope,
            UUID reversalTransactionId,
            InventoryTransactionRecord original,
            InventoryTransactionCommands.Reversal command) {
        boolean receipt = original.kind() == InventoryTransactionKind.RECEIPT;
        Optional<BigDecimal> unitCost = receipt ? original.unitCostVnd() : Optional.empty();
        BigDecimal procurementEffect = receipt
                ? InventoryNumbers.money(
                        command.quantityBase(), unitCost.orElseThrow()).negate()
                : BigDecimal.ZERO;
        return new InventoryTransactionRecord(
                reversalTransactionId,
                scope.tenantId(),
                original.warehouseId(),
                original.materialId(),
                InventoryTransactionKind.REVERSAL,
                original.unit(),
                command.quantityBase(),
                receipt ? command.quantityBase().negate() : command.quantityBase(),
                unitCost,
                procurementEffect,
                receipt ? original.supplierId() : Optional.empty(),
                receipt ? original.batchCode() : Optional.empty(),
                receipt ? original.expiryDate() : Optional.empty(),
                ledger.currentTimestamp(),
                Optional.of(command.reason()),
                Optional.empty(),
                Optional.of(original.id()),
                scope.profileId(),
                0);
    }
}
