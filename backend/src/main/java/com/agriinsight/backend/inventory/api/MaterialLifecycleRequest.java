package com.agriinsight.backend.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MaterialLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public MaterialLifecycleRequest {
        reasonCode = MaterialCreateRequest.normalizeReason(reasonCode);
    }
}
