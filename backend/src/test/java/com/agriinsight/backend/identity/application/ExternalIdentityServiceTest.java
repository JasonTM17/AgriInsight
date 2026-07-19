package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalIdentityServiceTest {

    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String SUBJECT = " Provider-Subject-001 ";

    @Mock
    private TenantPrincipalLoader principalLoader;

    @InjectMocks
    private ExternalIdentityService service;

    @Test
    void returnsTheDatabaseEnrichedPrincipal() {
        ExternalIdentityClaims claims = claims();
        AgriInsightPrincipal expected = principal();
        when(principalLoader.load(claims)).thenReturn(expected);

        assertThat(service.resolve(claims)).isSameAs(expected);
        verify(principalLoader).load(claims);
    }

    @Test
    void preservesTypedRejectionWithoutEchoingIdentifiers() {
        ExternalIdentityClaims claims = claims();
        when(principalLoader.load(claims))
                .thenThrow(new IdentityRejectedException(IdentityRejectionReason.UNKNOWN_IDENTITY));

        assertThatThrownBy(() -> service.resolve(claims))
                .isInstanceOf(IdentityRejectedException.class)
                .hasMessage("External identity is not active")
                .extracting(exception -> ((IdentityRejectedException) exception).reason())
                .isEqualTo(IdentityRejectionReason.UNKNOWN_IDENTITY);
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
        return new ExternalIdentityClaims(
                ISSUER,
                SUBJECT,
                "Untrusted token display",
                "untrusted@example.test",
                "mfa");
    }

    private AgriInsightPrincipal principal() {
        return new AgriInsightPrincipal(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "TENANT-A",
                Optional.of("Database Display"),
                Optional.of("database@example.test"),
                Optional.of("mfa"),
                Set.of(),
                Set.of());
    }
}
