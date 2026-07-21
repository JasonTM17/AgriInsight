package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Season;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.Locale;

public record SeasonTransitionRequest(
        @NotNull Season.Status targetStatus,
        @NotNull LocalDate effectiveDate,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public SeasonTransitionRequest {
        reasonCode = reasonCode == null ? null : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
