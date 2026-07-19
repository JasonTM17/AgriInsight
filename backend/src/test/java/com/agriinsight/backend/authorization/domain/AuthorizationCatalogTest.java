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
    }
}
