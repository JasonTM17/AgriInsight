package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.operations.domain.Employee;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EmployeeRecord(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        Optional<String> jobTitle,
        boolean active,
        long version) {

    public EmployeeRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = Employee.canonicalCode(code);
        displayName = Employee.canonicalDisplayName(displayName);
        jobTitle = Employee.optionalJobTitle(jobTitle);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
