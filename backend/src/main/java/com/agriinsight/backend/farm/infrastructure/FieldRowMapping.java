package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.farm.application.FieldRecord;
import com.agriinsight.backend.farm.domain.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class FieldRowMapping {

    static final String SELECT_COLUMNS = """
            field.id, field.tenant_id, field.farm_id, field.code, field.display_name,
            field.area_hectares, field.responsible_employee_id, field.latitude,
            field.longitude, field.soil_type, field.irrigation_type,
            field.active, field.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, farm_id, code, display_name, area_hectares,
            responsible_employee_id, latitude, longitude, soil_type,
            irrigation_type, active, version
            """;
    static final RowMapper<FieldRecord> MAPPER = (result, rowNumber) -> new FieldRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getObject("farm_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            result.getBigDecimal("area_hectares"),
            Optional.ofNullable(result.getObject("responsible_employee_id", UUID.class)),
            coordinates(result.getBigDecimal("latitude"), result.getBigDecimal("longitude")),
            Optional.ofNullable(result.getString("soil_type")),
            Optional.ofNullable(result.getString("irrigation_type")),
            result.getBoolean("active"),
            result.getLong("version"));

    private FieldRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Field query returned more than one row");
        }
        return rows.stream().findFirst();
    }

    private static Optional<Field.Coordinates> coordinates(
            BigDecimal latitude,
            BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            return Optional.empty();
        }
        if (latitude == null || longitude == null) {
            throw new IllegalStateException("Field coordinates are incomplete");
        }
        return Optional.of(new Field.Coordinates(latitude, longitude));
    }
}
