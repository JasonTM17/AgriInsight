package com.agriinsight.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record TenantExternalIdentityRequest(
        @NotBlank @Size(max = 2048) String issuer,
        @NotBlank @Size(max = 512) String subject,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public TenantExternalIdentityRequest {
        reasonCode = reasonCode == null || reasonCode.isBlank()
                ? null
                : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
