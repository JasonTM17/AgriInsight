package com.agriinsight.backend.farm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.farm.api.FieldCreateRequest;
import com.agriinsight.backend.farm.api.FieldUpdateRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldRequestContractsTest {

    @Test
    void createCanonicalizesExactValuesAndCoordinatePair() {
        FieldCreateRequest request = new FieldCreateRequest(
                " field-a ", " North Field ", new BigDecimal("12.5000"), null,
                new BigDecimal("10.123400"), new BigDecimal("106.765400"),
                " Loam ", " Drip ", "field_create");

        assertThat(request.code()).isEqualTo("FIELD-A");
        assertThat(request.areaHectares()).isEqualByComparingTo("12.5");
        assertThat(request.coordinates().orElseThrow().latitude()).isEqualByComparingTo("10.1234");
        assertThat(request.reasonCode()).isEqualTo("FIELD_CREATE");
    }

    @Test
    void coordinatesMustBeProvidedAndClearedAsOneValue() {
        assertThatThrownBy(() -> new FieldCreateRequest(
                "FIELD-A", "Field", BigDecimal.ONE, null,
                BigDecimal.TEN, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
        assertThatThrownBy(() -> update(
                null, true, BigDecimal.TEN, new BigDecimal("106")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinates cannot be set and cleared");
    }

    @Test
    void explicitNullableClearsAreRealPatchValues() {
        FieldUpdateRequest request = update(
                UUID.fromString("33000000-0000-0000-0000-000000000001"),
                false, null, null);

        assertThat(request.responsibleEmployeeId()).isNotNull();
        assertThat(request.clearSoilType()).isTrue();
        assertThat(request.clearCoordinates()).isFalse();
    }

    private FieldUpdateRequest update(
            UUID responsibleEmployeeId,
            Boolean clearCoordinates,
            BigDecimal latitude,
            BigDecimal longitude) {
        return new FieldUpdateRequest(
                null, null, null, responsibleEmployeeId, false,
                latitude, longitude, clearCoordinates,
                null, true, null, false, "field_update");
    }
}
