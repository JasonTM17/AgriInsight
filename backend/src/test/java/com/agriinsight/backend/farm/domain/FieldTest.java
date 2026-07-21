package com.agriinsight.backend.farm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");

    @Test
    void canonicalizesMasterDataAndExactCoordinates() {
        Field field = field(
                new BigDecimal("12.5000"),
                Optional.of(new Field.Coordinates(
                        new BigDecimal("10.123400"), new BigDecimal("106.765400"))));

        assertThat(field.code()).isEqualTo("FIELD-A");
        assertThat(field.displayName()).isEqualTo("North Field");
        assertThat(field.areaHectares()).isEqualByComparingTo("12.5");
        assertThat(field.coordinates().orElseThrow().latitude()).isEqualByComparingTo("10.1234");
        assertThat(field.soilType()).contains("Loam");
    }

    @Test
    void rejectsInvalidAreaCoordinateRangesAndPrecision() {
        assertThatThrownBy(() -> field(BigDecimal.ZERO, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new Field.Coordinates(
                new BigDecimal("90.000001"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
        assertThatThrownBy(() -> new Field.Coordinates(
                BigDecimal.ZERO, new BigDecimal("1.0000001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
    }

    @Test
    void rejectsBlankOptionalTextInsteadOfPersistingAmbiguousNulls() {
        assertThatThrownBy(() -> new Field(
                FIELD_ID, TENANT_ID, FARM_ID, "FIELD-A", "Field", BigDecimal.ONE,
                Optional.empty(), Optional.empty(), Optional.of("  "), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("soilType");
    }

    private Field field(BigDecimal area, Optional<Field.Coordinates> coordinates) {
        return new Field(
                FIELD_ID, TENANT_ID, FARM_ID, " field-a ", " North Field ", area,
                Optional.empty(), coordinates, Optional.of(" Loam "), Optional.of(" Drip "));
    }
}
