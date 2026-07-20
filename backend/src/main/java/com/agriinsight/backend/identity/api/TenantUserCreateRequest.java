package com.agriinsight.backend.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record TenantUserCreateRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 2048) String issuer,
        @NotBlank @Size(max = 512) String subject,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public TenantUserCreateRequest {
        displayName = displayName == null ? null : displayName.strip();
        email = email == null || email.isBlank() ? null : email.strip();
        reasonCode = normalizeReason(reasonCode);
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
