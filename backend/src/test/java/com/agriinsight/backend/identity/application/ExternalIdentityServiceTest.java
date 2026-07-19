package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalIdentityServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String SUBJECT = " Provider-Subject-001 ";

    @Mock
    private IdentityBootstrapPort bootstrapPort;

    @InjectMocks
    private ExternalIdentityService service;

    @Test
    void resolvesExactVerifiedIdentityToMinimumPrincipal() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                ISSUER,
                SUBJECT,
                "Lan Nguyen",
                "lan.nguyen@example.test",
                "mfa");
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, true)));

        AgriInsightPrincipal principal = service.resolve(claims);

        assertThat(principal.profileId()).isEqualTo(PROFILE_ID);
        assertThat(principal.tenantId()).isEqualTo(TENANT_ID);
        assertThat(principal.displayName()).contains("Lan Nguyen");
        assertThat(principal.email()).contains("lan.nguyen@example.test");
        assertThat(principal.assurance()).contains("mfa");
        verify(bootstrapPort).findByIssuerAndSubject(ISSUER, SUBJECT);
    }

    @Test
    void rejectsUnknownIdentityWithoutEchoingSecurityIdentifiers() {
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(claims()))
                .isInstanceOf(IdentityRejectedException.class)
                .extracting(exception -> ((IdentityRejectedException) exception).reason())
                .isEqualTo(IdentityRejectionReason.UNKNOWN_IDENTITY);
        assertThatThrownBy(() -> service.resolve(claims()))
                .hasMessage("External identity is not active");
    }

    @Test
    void rejectsDisabledProfile() {
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new IdentityBootstrap(PROFILE_ID, TENANT_ID, false, true)));

        assertThatThrownBy(() -> service.resolve(claims()))
                .isInstanceOfSatisfying(IdentityRejectedException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(IdentityRejectionReason.PROFILE_DISABLED));
    }

    @Test
    void rejectsDisabledTenant() {
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, false)));

        assertThatThrownBy(() -> service.resolve(claims()))
                .isInstanceOfSatisfying(IdentityRejectedException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(IdentityRejectionReason.TENANT_DISABLED));
    }

    @Test
    void rejectsBlankOrOversizedClaimsWithoutNormalizingTheSubject() {
        assertThatThrownBy(() -> new ExternalIdentityClaims("", SUBJECT, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalIdentityClaims(ISSUER, "", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalIdentityClaims(ISSUER, SUBJECT, "x".repeat(201), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ExternalIdentityClaims claims() {
        return new ExternalIdentityClaims(ISSUER, SUBJECT, null, null, null);
    }
}
