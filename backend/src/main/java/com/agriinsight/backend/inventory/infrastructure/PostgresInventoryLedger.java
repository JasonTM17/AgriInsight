package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresInventoryLedger {

    private final JdbcTemplate jdbcTemplate;

    PostgresInventoryLedger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<InventoryTransactionRecord> find(
            ScopeContext scope, UUID transactionId, boolean lock) {
        UUID target = Objects.requireNonNull(transactionId, "transactionId is required");
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(InventoryTransactionRowMapping.SELECT_COLUMNS)
                .append("""
                         FROM inventory_transactions AS transaction
                         JOIN warehouses AS warehouse
                           ON warehouse.tenant_id = transaction.tenant_id
                          AND warehouse.id = transaction.warehouse_id
                        WHERE transaction.tenant_id = ? AND transaction.id = ?
                        """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(scope.tenantId());
        parameters.add(target);
        WarehouseScopeSql.append(sql, parameters, scope, null);
        if (lock) {
            sql.append(" FOR UPDATE OF transaction");
        }
        return InventoryTransactionRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), InventoryTransactionRowMapping.MAPPER, parameters.toArray()));
    }

    InventoryTransactionRecord insert(InventoryTransactionRecord transaction) {
        Objects.requireNonNull(transaction, "transaction is required");
        return InventoryTransactionRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO inventory_transactions AS transaction (
                    id, tenant_id, warehouse_id, material_id, kind, unit_code,
                    quantity_base, signed_quantity_effect, unit_cost_vnd,
                    procurement_effect_vnd, supplier_id, batch_code, expiry_date,
                    occurred_at, reason, reference_code, reversal_of,
                    recorded_by_profile_id, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(InventoryTransactionRowMapping.SELECT_COLUMNS),
                InventoryTransactionRowMapping.MAPPER,
                transaction.id(), transaction.tenantId(), transaction.warehouseId(),
                transaction.materialId(), transaction.kind().name(), transaction.unit().name(),
                transaction.quantityBase(), transaction.signedQuantityEffect(),
                nullable(transaction.unitCostVnd().orElse(null), Types.NUMERIC),
                transaction.procurementEffectVnd(),
                nullable(transaction.supplierId().orElse(null), Types.OTHER),
                nullable(transaction.batchCode().orElse(null), Types.VARCHAR),
                nullable(transaction.expiryDate().orElse(null), Types.DATE),
                Timestamp.from(transaction.occurredAt()),
                nullable(transaction.reason().orElse(null), Types.VARCHAR),
                nullable(transaction.referenceCode().orElse(null), Types.VARCHAR),
                nullable(transaction.reversalOf().orElse(null), Types.OTHER),
                transaction.recordedByProfileId(),
                transaction.version()))
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory transaction was not inserted"));
    }

    Instant currentTimestamp() {
        Instant timestamp = jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP", (result, rowNumber) ->
                        result.getTimestamp(1).toInstant());
        return Objects.requireNonNull(timestamp, "Database timestamp is required");
    }

    void incrementVersion(ScopeContext scope, UUID transactionId, long expectedVersion) {
        int updated = jdbcTemplate.update("""
                UPDATE inventory_transactions AS transaction
                   SET version = transaction.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE transaction.tenant_id = ?
                   AND transaction.id = ?
                   AND transaction.version = ?
                """, scope.tenantId(), transactionId, expectedVersion);
        if (updated != 1) {
            throw new IllegalStateException("Locked inventory transaction version changed");
        }
    }

    private Object nullable(Object value, int sqlType) {
        return value == null ? new SqlParameterValue(sqlType, null) : value;
    }
}
