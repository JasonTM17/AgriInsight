package com.agriinsight.backend.farm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CropTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");

    @Test
    void canonicalizesSharedCatalogData() {
        Crop crop = new Crop(
                CROP_ID, TENANT_ID, " coffee-a ", " Arabica Coffee ",
                Optional.of(" Coffea arabica "));

        assertThat(crop.code()).isEqualTo("COFFEE-A");
        assertThat(crop.displayName()).isEqualTo("Arabica Coffee");
        assertThat(crop.scientificName()).contains("Coffea arabica");
    }

    @Test
    void rejectsBlankScientificNames() {
        assertThatThrownBy(() -> new Crop(
                CROP_ID, TENANT_ID, "COFFEE-A", "Coffee", Optional.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scientificName");
    }
}
