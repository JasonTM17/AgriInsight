package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.Employee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record EmployeeCreateRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 160) String jobTitle,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public EmployeeCreateRequest {
        code = code == null ? null : Employee.canonicalCode(code);
        displayName = displayName == null ? null : Employee.canonicalDisplayName(displayName);
        jobTitle = jobTitle == null ? null : Employee.optionalJobTitle(java.util.Optional.of(jobTitle)).orElseThrow();
        reasonCode = normalizeReason(reasonCode);
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
