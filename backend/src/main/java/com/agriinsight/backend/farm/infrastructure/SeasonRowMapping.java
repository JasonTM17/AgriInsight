package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.farm.application.SeasonRecord;
import com.agriinsight.backend.farm.domain.Season;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class SeasonRowMapping {

    static final String SELECT_COLUMNS = """
            season.id, season.tenant_id, season.farm_id, season.field_id, season.crop_id,
            season.code, season.display_name, season.variety_name,
            season.planned_start_date, season.planned_end_date,
            season.started_on, season.ended_on, season.planted_area_hectares,
            season.budget_vnd, season.status, season.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, farm_id, field_id, crop_id, code, display_name, variety_name,
            planned_start_date, planned_end_date, started_on, ended_on,
            planted_area_hectares, budget_vnd, status, version
            """;
    static final RowMapper<SeasonRecord> MAPPER = (result, rowNumber) -> new SeasonRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getObject("farm_id", UUID.class),
            result.getObject("field_id", UUID.class),
            result.getObject("crop_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            Optional.ofNullable(result.getString("variety_name")),
            result.getObject("planned_start_date", LocalDate.class),
            result.getObject("planned_end_date", LocalDate.class),
            Optional.ofNullable(result.getObject("started_on", LocalDate.class)),
            Optional.ofNullable(result.getObject("ended_on", LocalDate.class)),
            result.getBigDecimal("planted_area_hectares"),
            Optional.ofNullable(result.getBigDecimal("budget_vnd")),
            Season.Status.valueOf(result.getString("status")),
            result.getLong("version"));

    private SeasonRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Season query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
