package com.agriinsight.backend.operations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmployeeLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public EmployeeLifecycleRequest {
        reasonCode = EmployeeCreateRequest.normalizeReason(reasonCode);
    }
}
