package com.agriinsight.backend.operations.domain;

import java.math.BigDecimal;
import java.util.Objects;

public enum HarvestMassUnit {
    KG("1"),
    TONNE("1000");

    private final BigDecimal kilograms;

    HarvestMassUnit(String kilograms) {
        this.kilograms = new BigDecimal(kilograms);
    }

    public BigDecimal toKilograms(BigDecimal value, String fieldName) {
        BigDecimal required = Objects.requireNonNull(value, fieldName + " is required");
        if (required.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return Harvest.bounded(required.multiply(kilograms), fieldName + "Kg", 15, 3);
    }
}
