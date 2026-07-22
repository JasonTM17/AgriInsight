package com.agriinsight.backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HarvestTest {

    @Test
    void tonneInputNormalizesToCanonicalKilograms() {
        assertThat(HarvestMassUnit.TONNE.toKilograms(new BigDecimal("1.250"), "quantity"))
                .isEqualByComparingTo("1250");
    }

    @Test
    void harvestRejectsWasteAboveGrossQuantity() {
        assertThatThrownBy(() -> harvest(
                new BigDecimal("100"), new BigDecimal("101"),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wasteQuantityKg");
    }

    @Test
    void voidCorrectionRequiresEmptyBusinessValues() {
        assertThat(new Harvest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2027-09-01"),
                BigDecimal.ZERO, BigDecimal.ZERO, Optional.empty(), Optional.empty(),
                Optional.of(UUID.randomUUID()), Optional.of(HarvestCorrectionKind.VOID),
                Optional.of("Duplicate entry")).correctionKind())
                .contains(HarvestCorrectionKind.VOID);

        assertThatThrownBy(() -> harvest(
                BigDecimal.ZERO, BigDecimal.ZERO, Optional.of("A"),
                Optional.of(HarvestCorrectionKind.VOID), Optional.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("void correction");
    }

    private Harvest harvest(
            BigDecimal quantity, BigDecimal waste, Optional<String> grade,
            Optional<HarvestCorrectionKind> kind, Optional<UUID> corrects) {
        return new Harvest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2027-09-01"),
                quantity, waste, grade, Optional.empty(), corrects, kind,
                kind.map(ignored -> "Correction"));
    }
}
