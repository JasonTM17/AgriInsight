package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.operations.application.ActivityLogRecord;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class ActivityLogRowMapping {

    static final String COLUMNS = """
            id, tenant_id, activity_id, employee_id, author_profile_id, occurred_at,
            notes, quantity, unit_code, evidence_uri, corrects_log_id,
            correction_kind, correction_reason, version
            """;

    static final RowMapper<ActivityLogRecord> MAPPER = (result, rowNumber) -> {
        Timestamp occurredAt = result.getTimestamp("occurred_at");
        return new ActivityLogRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("activity_id", UUID.class),
                result.getObject("employee_id", UUID.class),
                result.getObject("author_profile_id", UUID.class),
                occurredAt.toInstant(),
                Optional.ofNullable(result.getString("notes")),
                Optional.ofNullable(result.getBigDecimal("quantity")),
                Optional.ofNullable(result.getString("unit_code")).map(ActivityLogUnit::valueOf),
                Optional.ofNullable(result.getString("evidence_uri")),
                Optional.ofNullable(result.getObject("corrects_log_id", UUID.class)),
                Optional.ofNullable(result.getString("correction_kind"))
                        .map(ActivityLogCorrectionKind::valueOf),
                Optional.ofNullable(result.getString("correction_reason")),
                result.getLong("version"));
    };

    private ActivityLogRowMapping() {
    }

    static Optional<ActivityLogRecord> exactlyOneOrEmpty(List<ActivityLogRecord> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Activity log query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
