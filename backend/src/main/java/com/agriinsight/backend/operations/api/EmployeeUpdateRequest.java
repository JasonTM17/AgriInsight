package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.Employee;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployeeUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 200) String displayName,
        @Size(max = 160) String jobTitle,
        Boolean clearJobTitle,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public EmployeeUpdateRequest {
        code = code == null ? null : Employee.canonicalCode(code);
        displayName = displayName == null ? null : Employee.canonicalDisplayName(displayName);
        jobTitle = jobTitle == null ? null : Employee.optionalJobTitle(java.util.Optional.of(jobTitle)).orElseThrow();
        clearJobTitle = Boolean.TRUE.equals(clearJobTitle);
        reasonCode = EmployeeCreateRequest.normalizeReason(reasonCode);
        if (jobTitle != null && clearJobTitle) {
            throw new IllegalArgumentException("jobTitle cannot be set and cleared together");
        }
        if (code == null && displayName == null && jobTitle == null && !clearJobTitle) {
            throw new IllegalArgumentException("at least one employee value must be provided");
        }
    }
}
