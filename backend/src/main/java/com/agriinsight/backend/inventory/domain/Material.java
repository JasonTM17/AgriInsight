package com.agriinsight.backend.inventory.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record Material(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        CanonicalUnit baseUnit,
        Optional<BigDecimal> minimumStockQuantity) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    public Material {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = canonicalCode(code);
        displayName = canonicalDisplayName(displayName);
        Objects.requireNonNull(baseUnit, "baseUnit is required");
        minimumStockQuantity = optionalMinimumStock(minimumStockQuantity);
    }

    public static String canonicalCode(String value) {
        String normalized = requiredText(value, "code", 64).toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code has an invalid format");
        }
        return normalized;
    }

    public static String canonicalDisplayName(String value) {
        return requiredText(value, "displayName", 160);
    }

    public static Optional<BigDecimal> optionalMinimumStock(Optional<BigDecimal> value) {
        return Objects.requireNonNull(value, "minimumStockQuantity is required")
                .map(Material::minimumStock);
    }

    public static BigDecimal minimumStock(BigDecimal value) {
        BigDecimal required = Objects.requireNonNull(value, "minimumStockQuantity is required");
        if (required.signum() < 0) {
            throw new IllegalArgumentException("minimumStockQuantity must not be negative");
        }
        BigDecimal normalized = required.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int wholeDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (scale > 4 || wholeDigits > 16) {
            throw new IllegalArgumentException("minimumStockQuantity exceeds supported precision");
        }
        return new BigDecimal(normalized.toPlainString());
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " is required").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
