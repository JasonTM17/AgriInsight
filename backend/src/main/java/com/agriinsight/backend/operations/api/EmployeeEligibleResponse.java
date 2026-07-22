package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.EmployeeRecord;
import java.util.Objects;
import java.util.UUID;

public record EmployeeEligibleResponse(
        UUID id,
        String code,
        String displayName,
        boolean active) {

    public EmployeeEligibleResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    public static EmployeeEligibleResponse from(EmployeeRecord employee) {
        Objects.requireNonNull(employee, "employee is required");
        return new EmployeeEligibleResponse(
                employee.id(), employee.code(), employee.displayName(), employee.active());
    }
}
