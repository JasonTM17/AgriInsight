package com.agriinsight.backend.farm.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record FarmLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public FarmLifecycleRequest {
        reasonCode = reasonCode == null ? null : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
