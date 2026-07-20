package com.agriinsight.backend.farm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FarmTest {

    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void canonicalizesExternalBusinessFields() {
        Farm farm = new Farm(FARM_ID, TENANT_ID, " north_01 ", " North Farm ");

        assertThat(farm.code()).isEqualTo("NORTH_01");
        assertThat(farm.displayName()).isEqualTo("North Farm");
    }

    @Test
    void rejectsInvalidCodesAndNamesAtTheDomainBoundary() {
        assertThatThrownBy(() -> new Farm(FARM_ID, TENANT_ID, "bad code", "North Farm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        assertThatThrownBy(() -> new Farm(FARM_ID, TENANT_ID, "NORTH", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName");
    }
}
