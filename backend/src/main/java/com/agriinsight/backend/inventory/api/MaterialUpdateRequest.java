package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MaterialUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 160) String displayName,
        @Pattern(regexp = "KG|LITER|PIECE") String baseUnit,
        @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal minimumStockQuantity,
        Boolean clearMinimumStockQuantity,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public MaterialUpdateRequest {
        code = code == null ? null : Material.canonicalCode(code);
        displayName = displayName == null ? null : Material.canonicalDisplayName(displayName);
        baseUnit = baseUnit == null ? null : CanonicalUnit.parse(baseUnit).name();
        minimumStockQuantity = minimumStockQuantity == null
                ? null : Material.minimumStock(minimumStockQuantity);
        clearMinimumStockQuantity = Boolean.TRUE.equals(clearMinimumStockQuantity);
        reasonCode = MaterialCreateRequest.normalizeReason(reasonCode);
        if (minimumStockQuantity != null && clearMinimumStockQuantity) {
            throw new IllegalArgumentException(
                    "minimumStockQuantity cannot be set and cleared together");
        }
        if (code == null && displayName == null && baseUnit == null
                && minimumStockQuantity == null && !clearMinimumStockQuantity) {
            throw new IllegalArgumentException("at least one material field must be provided");
        }
    }
}
