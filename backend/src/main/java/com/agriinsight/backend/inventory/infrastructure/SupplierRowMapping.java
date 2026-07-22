package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.inventory.application.SupplierRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class SupplierRowMapping {

    static final String SELECT_COLUMNS = """
            supplier.id, supplier.tenant_id, supplier.code, supplier.display_name,
            supplier.active, supplier.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, code, display_name, active, version
            """;
    static final RowMapper<SupplierRecord> MAPPER = (result, rowNumber) -> new SupplierRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            result.getBoolean("active"),
            result.getLong("version"));

    private SupplierRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Supplier query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
