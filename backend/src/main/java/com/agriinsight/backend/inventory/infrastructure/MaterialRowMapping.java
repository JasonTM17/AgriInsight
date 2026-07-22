package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.inventory.application.MaterialRecord;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class MaterialRowMapping {

    static final String SELECT_COLUMNS = """
            material.id, material.tenant_id, material.code, material.display_name,
            material.base_unit, material.minimum_stock_quantity,
            material.active, material.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, code, display_name, base_unit, minimum_stock_quantity,
            active, version
            """;
    static final RowMapper<MaterialRecord> MAPPER = (result, rowNumber) -> new MaterialRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            CanonicalUnit.valueOf(result.getString("base_unit")),
            Optional.ofNullable(result.getBigDecimal("minimum_stock_quantity")),
            result.getBoolean("active"),
            result.getLong("version"));

    private MaterialRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Material query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
