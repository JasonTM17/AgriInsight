package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.StockBalancePage;
import com.agriinsight.backend.inventory.application.StockBalanceQuery;
import com.agriinsight.backend.inventory.application.StockBalanceRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresStockBalanceQueries {

    private static final String LOW_STOCK =
            "material.minimum_stock_quantity IS NOT NULL "
                    + "AND balance.quantity_on_hand <= material.minimum_stock_quantity";

    private final JdbcTemplate jdbcTemplate;

    PostgresStockBalanceQueries(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    StockBalancePage findAll(ScopeContext scope, StockBalanceQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("""
                SELECT balance.id, balance.warehouse_id, warehouse.code AS warehouse_code,
                       balance.material_id, material.code AS material_code,
                       material.display_name AS material_name, balance.unit_code,
                       balance.quantity_on_hand, balance.inventory_value_vnd,
                       material.minimum_stock_quantity, (
                """).append(LOW_STOCK).append("""
                       ) AS low_stock, balance.version
                  FROM stock_balances AS balance
                  JOIN warehouses AS warehouse
                    ON warehouse.tenant_id = balance.tenant_id
                   AND warehouse.id = balance.warehouse_id
                  JOIN materials AS material
                    ON material.tenant_id = balance.tenant_id
                   AND material.id = balance.material_id
                 WHERE balance.tenant_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(scope.tenantId());
        WarehouseScopeSql.append(sql, parameters, scope, null);
        query.warehouseId().ifPresent(value -> add(
                sql, parameters, "balance.warehouse_id", value));
        query.materialId().ifPresent(value -> add(
                sql, parameters, "balance.material_id", value));
        query.lowStock().ifPresent(value -> sql.append(" AND (")
                .append(LOW_STOCK).append(") = ").append(value ? "TRUE" : "FALSE"));
        sql.append(" ORDER BY warehouse.code, material.code, balance.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<StockBalanceRecord> rows = jdbcTemplate.query(
                sql.toString(), (result, rowNumber) -> new StockBalanceRecord(
                        result.getObject("id", java.util.UUID.class),
                        result.getObject("warehouse_id", java.util.UUID.class),
                        result.getString("warehouse_code"),
                        result.getObject("material_id", java.util.UUID.class),
                        result.getString("material_code"),
                        result.getString("material_name"),
                        CanonicalUnit.valueOf(result.getString("unit_code")),
                        result.getBigDecimal("quantity_on_hand"),
                        result.getBigDecimal("inventory_value_vnd"),
                        Optional.ofNullable(result.getBigDecimal("minimum_stock_quantity")),
                        result.getBoolean("low_stock"),
                        result.getLong("version")),
                parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<StockBalanceRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new StockBalancePage(items, query.limit(), query.offset(), hasMore);
    }

    private void add(
            StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }
}
