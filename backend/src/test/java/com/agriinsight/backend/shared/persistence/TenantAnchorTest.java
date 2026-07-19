package com.agriinsight.backend.shared.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantAnchorTest {

    @Test
    void canonicalizesBusinessCodeAndDisplayNameAtCreation() {
        var tenant = new TenantAnchor(UUID.randomUUID(), "\u00a0farm-vn-01\u00a0", "  Mekong Farm  ");

        assertThat(tenant.getCode()).isEqualTo("FARM-VN-01");
        assertThat(tenant.getDisplayName()).isEqualTo("Mekong Farm");
        assertThat(tenant.isActive()).isTrue();
        assertThat(tenant.getVersion()).isZero();
    }

    @Test
    void rejectsMissingOrBlankIdentityFieldsBeforePersistence() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new TenantAnchor(null, "FARM-01", "Farm"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantAnchor(id, " ", "Farm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantAnchor(id, "FARM-01", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantAnchor(id, "FARM ĐB", "Farm"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
