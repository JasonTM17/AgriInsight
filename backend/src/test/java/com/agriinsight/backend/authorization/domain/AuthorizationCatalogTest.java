package com.agriinsight.backend.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizationCatalogTest {

    @Test
    void fixedCatalogContainsSupplierWithoutImplicitPermissions() {
        assertThat(Role.values()).hasSize(7).contains(Role.SUPPLIER);
        assertThat(Permission.values()).hasSize(19);
    }
}
