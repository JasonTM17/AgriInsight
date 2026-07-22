package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.operations.application.ActivityRecord;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class ActivityRowMapping {

    static final String SELECT_COLUMNS = """
            activity.id, activity.tenant_id, activity.farm_id, activity.field_id,
            activity.season_id, activity.activity_type_code, activity.code,
            activity.title, activity.description, activity.planned_start_at,
            activity.due_at, activity.started_at, activity.completed_at,
            activity.cancelled_at, activity.status, activity.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, farm_id, field_id, season_id, activity_type_code,
            code, title, description, planned_start_at, due_at, started_at,
            completed_at, cancelled_at, status, version
            """;
    static final RowMapper<ActivityRecord> MAPPER = (result, rowNumber) -> new ActivityRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getObject("farm_id", UUID.class),
            result.getObject("field_id", UUID.class),
            result.getObject("season_id", UUID.class),
            ActivityType.valueOf(result.getString("activity_type_code")),
            result.getString("code"),
            result.getString("title"),
            Optional.ofNullable(result.getString("description")),
            instant(result, "planned_start_at"),
            instant(result, "due_at"),
            optionalInstant(result, "started_at"),
            optionalInstant(result, "completed_at"),
            optionalInstant(result, "cancelled_at"),
            ActivityStatus.valueOf(result.getString("status")),
            result.getLong("version"));

    private ActivityRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Activity query returned more than one row");
        }
        return rows.stream().findFirst();
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Optional<Instant> optionalInstant(ResultSet result, String column)
            throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }
}
