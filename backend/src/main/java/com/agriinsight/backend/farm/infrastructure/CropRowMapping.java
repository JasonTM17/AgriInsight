package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.farm.application.CropRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class CropRowMapping {

    static final String SELECT_COLUMNS = """
            crop.id, crop.tenant_id, crop.code, crop.display_name,
            crop.scientific_name, crop.active, crop.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, code, display_name, scientific_name, active, version
            """;
    static final RowMapper<CropRecord> MAPPER = (result, rowNumber) -> new CropRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            Optional.ofNullable(result.getString("scientific_name")),
            result.getBoolean("active"),
            result.getLong("version"));

    private CropRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Crop query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
