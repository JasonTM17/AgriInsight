package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.EmployeeRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String code,
        String displayName,
        Optional<String> jobTitle,
        boolean active,
        long version) {

    public EmployeeResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(jobTitle, "jobTitle is required");
    }

    public static EmployeeResponse from(EmployeeRecord employee) {
        Objects.requireNonNull(employee, "employee is required");
        return new EmployeeResponse(
                employee.id(), employee.code(), employee.displayName(), employee.jobTitle(),
                employee.active(), employee.version());
    }
}
