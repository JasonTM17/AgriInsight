package com.agriinsight.backend.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class InventoryNumbers {

    private InventoryNumbers() {
    }

    public static BigDecimal positiveQuantity(BigDecimal value) {
        BigDecimal normalized = normalized(value, "quantityBase", 16, 4);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("quantityBase must be positive");
        }
        return normalized;
    }

    public static BigDecimal nonnegativeUnitCost(BigDecimal value) {
        BigDecimal normalized = normalized(value, "unitCostVnd", 16, 2);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("unitCostVnd must not be negative");
        }
        return normalized;
    }

    public static BigDecimal money(BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal normalizedQuantity = positiveQuantity(quantity);
        BigDecimal normalizedCost = nonnegativeUnitCost(unitCost);
        BigDecimal amount = normalizedQuantity.multiply(normalizedCost)
                .setScale(2, RoundingMode.HALF_UP);
        return normalized(amount, "money", 18, 2);
    }

    public static BigDecimal signedMoney(BigDecimal value) {
        return normalized(value, "money", 18, 2);
    }

    private static BigDecimal normalized(
            BigDecimal value,
            String fieldName,
            int maxWholeDigits,
            int maxScale) {
        BigDecimal required = Objects.requireNonNull(value, fieldName + " is required");
        BigDecimal stripped = required.stripTrailingZeros();
        int scale = Math.max(stripped.scale(), 0);
        int wholeDigits = Math.max(stripped.precision() - stripped.scale(), 0);
        if (scale > maxScale || wholeDigits > maxWholeDigits) {
            throw new IllegalArgumentException(fieldName + " exceeds supported precision");
        }
        return new BigDecimal(stripped.toPlainString());
    }
}
