package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.StockLotPage;
import com.agriinsight.backend.inventory.application.StockLotQuery;
import com.agriinsight.backend.inventory.application.StockLotRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresStockLotQueries {

    private final JdbcTemplate jdbcTemplate;

    PostgresStockLotQueries(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    StockLotPage findAll(ScopeContext scope, StockLotQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("""
                SELECT lot.id, lot.warehouse_id, warehouse.code AS warehouse_code,
                       lot.material_id, material.code AS material_code,
                       material.display_name AS material_name,
                       lot.supplier_id, supplier.code AS supplier_code,
                       lot.original_receipt_id, lot.batch_code, lot.expiry_date,
                       lot.received_at, lot.unit_code, lot.received_quantity,
                       lot.available_quantity, lot.unit_cost_vnd,
                       lot.expiry_date < CURRENT_DATE AS expired,
                       lot.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30
                           AS expiring_soon,
                       lot.version
                  FROM stock_lots AS lot
                  JOIN warehouses AS warehouse
                    ON warehouse.tenant_id = lot.tenant_id
                   AND warehouse.id = lot.warehouse_id
                  JOIN materials AS material
                    ON material.tenant_id = lot.tenant_id
                   AND material.id = lot.material_id
                  JOIN suppliers AS supplier
                    ON supplier.tenant_id = lot.tenant_id
                   AND supplier.id = lot.supplier_id
                 WHERE lot.tenant_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(scope.tenantId());
        WarehouseScopeSql.append(sql, parameters, scope, null);
        query.warehouseId().ifPresent(value -> add(
                sql, parameters, "lot.warehouse_id = ?", value));
        query.materialId().ifPresent(value -> add(
                sql, parameters, "lot.material_id = ?", value));
        query.expiringBefore().ifPresent(value -> add(
                sql, parameters, "lot.expiry_date <= ?", value));
        if (!query.includeDepleted()) {
            sql.append(" AND lot.available_quantity > 0");
        }
        sql.append("""
                 ORDER BY lot.expiry_date, lot.received_at, lot.id
                 LIMIT ? OFFSET ?
                """);
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<StockLotRecord> rows = jdbcTemplate.query(
                sql.toString(), (result, rowNumber) -> new StockLotRecord(
                        result.getObject("id", java.util.UUID.class),
                        result.getObject("warehouse_id", java.util.UUID.class),
                        result.getString("warehouse_code"),
                        result.getObject("material_id", java.util.UUID.class),
                        result.getString("material_code"),
                        result.getString("material_name"),
                        result.getObject("supplier_id", java.util.UUID.class),
                        result.getString("supplier_code"),
                        result.getObject("original_receipt_id", java.util.UUID.class),
                        result.getString("batch_code"),
                        result.getObject("expiry_date", java.time.LocalDate.class),
                        result.getTimestamp("received_at").toInstant(),
                        CanonicalUnit.valueOf(result.getString("unit_code")),
                        result.getBigDecimal("received_quantity"),
                        result.getBigDecimal("available_quantity"),
                        result.getBigDecimal("unit_cost_vnd"),
                        result.getBoolean("expired"),
                        result.getBoolean("expiring_soon"),
                        result.getLong("version")),
                parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<StockLotRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new StockLotPage(items, query.limit(), query.offset(), hasMore);
    }

    private void add(
            StringBuilder sql, List<Object> parameters, String predicate, Object value) {
        sql.append(" AND ").append(predicate);
        parameters.add(value);
    }
}
