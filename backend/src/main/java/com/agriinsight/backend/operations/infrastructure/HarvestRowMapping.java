package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.operations.application.HarvestRecord;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class HarvestRowMapping {

    static final String COLUMNS = """
            id, tenant_id, farm_id, field_id, season_id, crop_id, recorded_by_profile_id,
            occurred_on, quantity_kg, waste_quantity_kg, quality_grade, revenue_vnd,
            corrects_harvest_id, correction_kind, correction_reason, version
            """;
    static final String SELECT_COLUMNS = """
            harvest.id, harvest.tenant_id, harvest.farm_id, harvest.field_id,
            harvest.season_id, harvest.crop_id, harvest.recorded_by_profile_id,
            harvest.occurred_on, harvest.quantity_kg, harvest.waste_quantity_kg,
            harvest.quality_grade, harvest.revenue_vnd, harvest.corrects_harvest_id,
            harvest.correction_kind, harvest.correction_reason, harvest.version
            """;
    static final RowMapper<HarvestRecord> MAPPER = (result, rowNumber) -> new HarvestRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getObject("farm_id", UUID.class),
            result.getObject("field_id", UUID.class),
            result.getObject("season_id", UUID.class),
            result.getObject("crop_id", UUID.class),
            result.getObject("recorded_by_profile_id", UUID.class),
            result.getObject("occurred_on", LocalDate.class),
            result.getBigDecimal("quantity_kg"),
            result.getBigDecimal("waste_quantity_kg"),
            Optional.ofNullable(result.getString("quality_grade")),
            Optional.ofNullable(result.getBigDecimal("revenue_vnd")),
            Optional.ofNullable(result.getObject("corrects_harvest_id", UUID.class)),
            Optional.ofNullable(result.getString("correction_kind")).map(HarvestCorrectionKind::valueOf),
            Optional.ofNullable(result.getString("correction_reason")),
            result.getLong("version"));

    private HarvestRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Harvest query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
