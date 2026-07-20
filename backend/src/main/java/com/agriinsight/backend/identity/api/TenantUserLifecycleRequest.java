package com.agriinsight.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantUserLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public TenantUserLifecycleRequest {
        reasonCode = reasonCode == null ? null : reasonCode.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
