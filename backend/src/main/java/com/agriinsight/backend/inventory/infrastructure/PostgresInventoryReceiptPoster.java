package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionCommands;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryReceiptPoster {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresInventoryLedger ledger;
    private final PostgresInventoryBalanceProjection balances;

    PostgresInventoryReceiptPoster(
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
            InventoryTransactionCommands.Receipt command) {
        balances.lock(scope, command.warehouseId(), command.materialId(), unit);
        InventoryTransactionRecord receipt = ledger.insert(new InventoryTransactionRecord(
                transactionId,
                scope.tenantId(),
                command.warehouseId(),
                command.materialId(),
                InventoryTransactionKind.RECEIPT,
                unit,
                command.quantityBase(),
                command.quantityBase(),
                Optional.of(command.unitCostVnd()),
                command.procurementEffectVnd(),
                Optional.of(command.supplierId()),
                Optional.of(command.batchCode()),
                Optional.of(command.expiryDate()),
                command.occurredAt(),
                Optional.empty(),
                command.referenceCode(),
                Optional.empty(),
                scope.profileId(),
                0));
        insertLot(scope, receipt);
        balances.recompute(scope, command.warehouseId(), command.materialId());
        return receipt;
    }

    private void insertLot(ScopeContext scope, InventoryTransactionRecord receipt) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO stock_lots (
                    id, tenant_id, warehouse_id, material_id, supplier_id,
                    original_receipt_id, batch_code, expiry_date, received_at,
                    unit_code, received_quantity, available_quantity, unit_cost_vnd)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), scope.tenantId(), receipt.warehouseId(),
                receipt.materialId(), receipt.supplierId().orElseThrow(), receipt.id(),
                receipt.batchCode().orElseThrow(), receipt.expiryDate().orElseThrow(),
                Timestamp.from(receipt.occurredAt()), receipt.unit().name(), receipt.quantityBase(),
                receipt.quantityBase(), receipt.unitCostVnd().orElse(BigDecimal.ZERO));
        if (inserted != 1) {
            throw new IllegalStateException("Receipt stock lot was not inserted");
        }
    }
}
