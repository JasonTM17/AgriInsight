package com.agriinsight.backend.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizationCatalogTest {

    @Test
    void fixedCatalogContainsSupplierWithoutImplicitPermissions() {
        assertThat(Role.values()).hasSize(7).contains(Role.SUPPLIER);
        assertThat(Permission.values()).hasSize(19);
        assertThat(Role.TENANT_ADMIN.authority()).isEqualTo("ROLE_TENANT_ADMIN");
        assertThat(Permission.IDENTITY_ROLE_MANAGE.authority()).isEqualTo("IDENTITY_ROLE_MANAGE");
        assertThat(Role.TENANT_ADMIN.permissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(Role.DATA_ANALYST.tenantWide()).isTrue();
        assertThat(Role.DATA_ANALYST.grants(Permission.FARM_READ)).isTrue();
        assertThat(Role.DATA_ANALYST.grants(Permission.FARM_MANAGE)).isFalse();
        assertThat(Role.FARM_MANAGER.tenantWide()).isFalse();
        assertThat(Role.FARM_MANAGER.grants(Permission.FARM_MANAGE)).isTrue();
        assertThat(Role.FARM_MANAGER.grants(Permission.COST_READ)).isTrue();
        assertThat(Role.FARM_MANAGER.grants(Permission.COST_MANAGE)).isFalse();
        assertThat(Role.EXECUTIVE.grants(Permission.COST_READ)).isTrue();
        assertThat(Role.DATA_ANALYST.grants(Permission.COST_READ)).isTrue();
        assertThat(Role.INVENTORY_MANAGER.grants(Permission.COST_READ)).isFalse();
        assertThat(Role.INVENTORY_MANAGER.grants(Permission.COST_MANAGE)).isFalse();
        assertThat(Role.SUPPLIER.permissions()).isEmpty();
    }
}
