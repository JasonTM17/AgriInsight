package com.agriinsight.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.application.MaterialCommands;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialContractTest {

    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    @Test
    void canonicalizesCodesUnitsAndThresholdPrecision() {
        var material = new Material(
                UUID.randomUUID(), UUID.randomUUID(), " fertilizer.n ", " Fertilizer N ",
                CanonicalUnit.parse(" kg "), Optional.of(new BigDecimal("12.5000")));

        assertThat(material.code()).isEqualTo("FERTILIZER.N");
        assertThat(material.baseUnit()).isEqualTo(CanonicalUnit.KG);
        assertThat(material.minimumStockQuantity()).contains(new BigDecimal("12.5"));
        assertThat(CanonicalUnit.parse("liter")).isEqualTo(CanonicalUnit.LITER);
    }

    @Test
    void rejectsUnsupportedUnitsAndThresholds() {
        assertThatThrownBy(() -> CanonicalUnit.parse("tonne"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KG, LITER, or PIECE");
        assertThatThrownBy(() -> Material.minimumStock(new BigDecimal("-0.0001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
        assertThatThrownBy(() -> Material.minimumStock(new BigDecimal("1.00001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
    }

    @Test
    void nullableThresholdPatchPreservesAbsentSetAndClearStates() {
        var clear = new MaterialCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Optional.empty()), 3, AUDIT);
        var set = new MaterialCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Optional.of(new BigDecimal("4.25"))), 3, AUDIT);

        assertThat(clear.minimumStockQuantity()).contains(Optional.empty());
        assertThat(set.minimumStockQuantity()).contains(Optional.of(new BigDecimal("4.25")));
    }
}
