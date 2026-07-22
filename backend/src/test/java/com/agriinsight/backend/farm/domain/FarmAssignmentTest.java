package com.agriinsight.backend.farm.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FarmAssignmentTest {

    private static final UUID ID = UUID.fromString("38000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void identityAndTenantLinksAreRequired() {
        assertThatThrownBy(() -> new FarmAssignment(null, TENANT_ID, PROFILE_ID, FARM_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FarmAssignment(ID, null, PROFILE_ID, FARM_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FarmAssignment(ID, TENANT_ID, null, FARM_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FarmAssignment(ID, TENANT_ID, PROFILE_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
