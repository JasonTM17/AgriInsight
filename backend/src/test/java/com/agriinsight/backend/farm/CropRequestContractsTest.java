package com.agriinsight.backend.farm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.farm.api.CropCreateRequest;
import com.agriinsight.backend.farm.api.CropUpdateRequest;
import org.junit.jupiter.api.Test;

class CropRequestContractsTest {

    @Test
    void createCanonicalizesSharedCatalogValues() {
        CropCreateRequest request = new CropCreateRequest(
                " coffee-a ", " Arabica Coffee ", " Coffea arabica ", "crop_create");

        assertThat(request.code()).isEqualTo("COFFEE-A");
        assertThat(request.displayName()).isEqualTo("Arabica Coffee");
        assertThat(request.scientificName()).isEqualTo("Coffea arabica");
        assertThat(request.reasonCode()).isEqualTo("CROP_CREATE");
    }

    @Test
    void explicitScientificNameClearIsARealPatchValue() {
        CropUpdateRequest request = new CropUpdateRequest(
                null, null, null, true, "crop_change");

        assertThat(request.clearScientificName()).isTrue();
        assertThat(request.reasonCode()).isEqualTo("CROP_CHANGE");
    }

    @Test
    void setAndClearCannotBeRequestedTogether() {
        assertThatThrownBy(() -> new CropUpdateRequest(
                null, null, "Coffea arabica", true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("set and cleared");
    }
}
