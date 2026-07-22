package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;

public record MaterialCreateRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Pattern(regexp = "KG|LITER|PIECE") String baseUnit,
        @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal minimumStockQuantity,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public MaterialCreateRequest {
        code = code == null ? null : Material.canonicalCode(code);
        displayName = displayName == null ? null : Material.canonicalDisplayName(displayName);
        baseUnit = baseUnit == null ? null : CanonicalUnit.parse(baseUnit).name();
        minimumStockQuantity = minimumStockQuantity == null
                ? null : Material.minimumStock(minimumStockQuantity);
        reasonCode = normalizeReason(reasonCode);
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank()
                ? null
                : value.strip().toUpperCase(Locale.ROOT);
    }
}
