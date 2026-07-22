package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.cost.application.OperatingCostRecord;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class OperatingCostRowMapping {

    static final String COLUMNS = """
            entry.id, entry.tenant_id, entry.target_type, entry.farm_id,
            entry.field_id, entry.season_id, entry.activity_id,
            entry.category_code, entry.amount_vnd, entry.entry_kind,
            entry.occurred_at, entry.description, entry.source_reference,
            entry.reversal_of, entry.command_reference,
            entry.recorded_by_profile_id, entry.version
            """;

    static final RowMapper<OperatingCostRecord> MAPPER =
            (result, rowNumber) -> map(result);

    private OperatingCostRowMapping() {
    }

    static Optional<OperatingCostRecord> exactlyOneOrEmpty(
            List<OperatingCostRecord> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Operating cost query returned multiple rows");
        }
        return rows.stream().findFirst();
    }

    private static OperatingCostRecord map(ResultSet result) throws SQLException {
        CostTarget.Type targetType = CostTarget.Type.valueOf(
                result.getString("target_type"));
        CostTarget target = targetType == CostTarget.Type.TENANT
                ? CostTarget.tenant()
                : CostTarget.domain(targetType, result.getObject(
                        targetColumn(targetType), UUID.class));
        return new OperatingCostRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                target,
                CostCategory.valueOf(result.getString("category_code")),
                result.getBigDecimal("amount_vnd"),
                CostEntryKind.valueOf(result.getString("entry_kind")),
                result.getTimestamp("occurred_at").toInstant(),
                Optional.ofNullable(result.getString("description")),
                Optional.ofNullable(result.getString("source_reference")),
                Optional.ofNullable(result.getObject("reversal_of", UUID.class)),
                result.getObject("command_reference", UUID.class),
                result.getObject("recorded_by_profile_id", UUID.class),
                result.getLong("version"));
    }

    private static String targetColumn(CostTarget.Type type) {
        return switch (type) {
            case FARM -> "farm_id";
            case FIELD -> "field_id";
            case SEASON -> "season_id";
            case ACTIVITY -> "activity_id";
            case TENANT -> throw new IllegalArgumentException(
                    "Tenant targets do not have a target id column");
        };
    }
}
