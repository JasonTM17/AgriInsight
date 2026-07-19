package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.IdentityRejectedException;
import com.agriinsight.backend.identity.application.IdentityRejectionReason;
import com.agriinsight.backend.identity.application.PrincipalMapper;
import com.agriinsight.backend.identity.domain.Permission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPrincipalAuthenticationConverterTest {

    @Test
    void discardsTheJwtAfterCreatingAnAuthenticatedInternalPrincipal() {
        PrincipalMapper mapper = mock(PrincipalMapper.class);
        Jwt jwt = jwt();
        AgriInsightPrincipal principal = new AgriInsightPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TENANT-A",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                Set.of(Permission.FARM_READ));
        when(mapper.map(jwt)).thenReturn(principal);

        AbstractAuthenticationToken authentication = new JwtPrincipalAuthenticationConverter(mapper).convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("FARM_READ");
        assertThat(authentication.toString()).doesNotContain(jwt.getTokenValue());
    }

    @Test
    void convertsIdentityRejectionIntoAGenericInvalidTokenFailure() {
        PrincipalMapper mapper = mock(PrincipalMapper.class);
        Jwt jwt = jwt();
        when(mapper.map(jwt)).thenThrow(new IdentityRejectedException(IdentityRejectionReason.UNKNOWN_IDENTITY));

        assertThatThrownBy(() -> new JwtPrincipalAuthenticationConverter(mapper).convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageNotContaining("UNKNOWN_IDENTITY")
                .hasMessageNotContaining(jwt.getSubject());
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("never-retain-this-token")
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("sensitive-subject")
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(60))
                .claim("token_use", "access")
                .build();
    }
}
