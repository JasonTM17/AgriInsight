package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.operations.application.EmployeeRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class EmployeeRowMapping {

    static final String SELECT_COLUMNS = """
            employee.id, employee.tenant_id, employee.code, employee.display_name,
            employee.job_title, employee.active, employee.version
            """;
    static final String RETURNING_COLUMNS = """
            id, tenant_id, code, display_name, job_title, active, version
            """;
    static final RowMapper<EmployeeRecord> MAPPER = (result, rowNumber) -> new EmployeeRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            Optional.ofNullable(result.getString("job_title")),
            result.getBoolean("active"),
            result.getLong("version"));

    private EmployeeRowMapping() {
    }

    static <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Employee query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
