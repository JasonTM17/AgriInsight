package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.InventoryTransactionPage;
import com.agriinsight.backend.inventory.application.InventoryTransactionQuery;
import com.agriinsight.backend.inventory.application.InventoryTransactionRecord;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresInventoryTransactionQueries {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresInventoryLedger ledger;

    PostgresInventoryTransactionQueries(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.ledger = new PostgresInventoryLedger(jdbcTemplate);
    }

    InventoryTransactionPage findAll(
            ScopeContext scope, InventoryTransactionQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(InventoryTransactionRowMapping.SELECT_COLUMNS)
                .append("""
                         FROM inventory_transactions AS transaction
                         JOIN warehouses AS warehouse
                           ON warehouse.tenant_id = transaction.tenant_id
                          AND warehouse.id = transaction.warehouse_id
                        WHERE transaction.tenant_id = ?
                        """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(scope.tenantId());
        WarehouseScopeSql.append(sql, parameters, scope, null);
        query.warehouseId().ifPresent(value -> add(sql, parameters,
                "transaction.warehouse_id", value));
        query.materialId().ifPresent(value -> add(sql, parameters,
                "transaction.material_id", value));
        query.kind().ifPresent(value -> add(sql, parameters,
                "transaction.kind", value.name()));
        query.occurredFrom().ifPresent(value -> add(sql, parameters,
                "transaction.occurred_at >=", Timestamp.from(value), false));
        query.occurredTo().ifPresent(value -> add(sql, parameters,
                "transaction.occurred_at <=", Timestamp.from(value), false));
        sql.append(" ORDER BY transaction.occurred_at DESC, transaction.id DESC LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<InventoryTransactionRecord> rows = jdbcTemplate.query(
                sql.toString(), InventoryTransactionRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<InventoryTransactionRecord> items = hasMore
                ? rows.subList(0, query.limit()) : rows;
        return new InventoryTransactionPage(items, query.limit(), query.offset(), hasMore);
    }

    Optional<InventoryTransactionRecord> findById(
            ScopeContext scope, UUID transactionId) {
        return ledger.find(scope, transactionId, false);
    }

    private void add(
            StringBuilder sql, List<Object> parameters, String column, Object value) {
        add(sql, parameters, column, value, true);
    }

    private void add(
            StringBuilder sql,
            List<Object> parameters,
            String expression,
            Object value,
            boolean equals) {
        sql.append(" AND ").append(expression).append(equals ? " = ?" : " ?");
        parameters.add(value);
    }
}
