package com.agriinsight.backend.authorization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record TenantRoleGrantRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String roleCode,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public TenantRoleGrantRequest {
        roleCode = roleCode == null ? null : roleCode.strip().toUpperCase(Locale.ROOT);
        reasonCode = reasonCode == null || reasonCode.isBlank()
                ? null
                : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
