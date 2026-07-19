package com.agriinsight.backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityDomainTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void profileActivationIsExplicitAndDisplayMetadataIsBounded() {
        UserProfile profile = new UserProfile(
                PROFILE_ID,
                TENANT_ID,
                "  Lan Nguyen  ",
                "lan.nguyen@example.test");

        assertThat(profile.getDisplayName()).isEqualTo("Lan Nguyen");
        assertThat(profile.isActive()).isTrue();

        profile.deactivate();
        assertThat(profile.isActive()).isFalse();
        profile.reactivate();
        assertThat(profile.isActive()).isTrue();

        assertThatThrownBy(() -> new UserProfile(PROFILE_ID, TENANT_ID, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserProfile(PROFILE_ID, TENANT_ID, "\u00A0\u0085", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalSecurityIdentifiersRemainExact() {
        String issuer = "https://identity.example.test/issuer";
        String subject = " Provider-Subject-001 ";
        ExternalIdentity identity = new ExternalIdentity(
                UUID.randomUUID(),
                TENANT_ID,
                PROFILE_ID,
                issuer,
                subject);

        assertThat(identity.getIssuer()).isEqualTo(issuer);
        assertThat(identity.getSubject()).isEqualTo(subject);
        identity.deactivate();
        assertThat(identity.isActive()).isFalse();
    }

    @Test
    void fixedAuthorizationCatalogContainsSupplierWithoutImplicitPermissions() {
        assertThat(Role.values()).hasSize(7).contains(Role.SUPPLIER);
        assertThat(Permission.values()).hasSize(19);
    }
}
