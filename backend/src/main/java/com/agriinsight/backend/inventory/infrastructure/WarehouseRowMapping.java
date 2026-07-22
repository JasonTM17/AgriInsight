package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.inventory.application.WarehouseRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class WarehouseRowMapping {

    static final String SELECT_COLUMNS = """
            warehouse.id, warehouse.tenant_id, warehouse.code, warehouse.display_name,
            warehouse.location_text, warehouse.active, warehouse.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, code, display_name, location_text, active, version
            """;
    static final RowMapper<WarehouseRecord> MAPPER = (result, rowNumber) -> new WarehouseRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            Optional.ofNullable(result.getString("location_text")),
            result.getBoolean("active"),
            result.getLong("version"));

    private WarehouseRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Warehouse query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
