package com.agriinsight.backend.inventory.domain;

import java.util.Locale;
import java.util.Objects;

public enum CanonicalUnit {
    KG,
    LITER,
    PIECE;

    public static CanonicalUnit parse(String value) {
        String normalized = Objects.requireNonNull(value, "unit is required")
                .strip()
                .toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unit must be KG, LITER, or PIECE", exception);
        }
    }
}
