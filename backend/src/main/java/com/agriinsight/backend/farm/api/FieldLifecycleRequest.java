package com.agriinsight.backend.farm.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FieldLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public FieldLifecycleRequest {
        reasonCode = FieldCreateRequest.normalizeReason(reasonCode);
    }
}
