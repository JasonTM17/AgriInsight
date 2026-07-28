package com.agriinsight.backend.realtime.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.ExternalIdentityClaims;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class RealtimeE2eIdentityFixture {

    static final String TENANT_A_TOKEN = "realtime-e2e-tenant-a";
    static final String TENANT_B_TOKEN = "realtime-e2e-tenant-b";

    private RealtimeE2eIdentityFixture() {
    }

    static void configure(JwtDecoder jwtDecoder, TenantPrincipalLoader principalLoader) {
        when(jwtDecoder.decode(TENANT_A_TOKEN)).thenReturn(jwt(TENANT_A_TOKEN, "realtime-e2e-tenant-a"));
        when(jwtDecoder.decode(TENANT_B_TOKEN)).thenReturn(jwt(TENANT_B_TOKEN, "realtime-e2e-tenant-b"));
        when(principalLoader.load(any())).thenAnswer(invocation -> {
            ExternalIdentityClaims claims = invocation.getArgument(0);
            return "realtime-e2e-tenant-b".equals(claims.subject())
                    ? principal(PostgresRealtimeE2eFixture.TENANT_B, PostgresRealtimeE2eFixture.PROFILE_B, "TENANT-B")
                    : principal(PostgresRealtimeE2eFixture.TENANT_A, PostgresRealtimeE2eFixture.PROFILE_A, "TENANT-A");
        });
    }

    private static AgriInsightPrincipal principal(UUID tenantId, UUID profileId, String tenantCode) {
        return new AgriInsightPrincipal(
                profileId, tenantId, tenantCode, Optional.of("Realtime E2E reader"), Optional.empty(),
                Optional.of("mfa"), Set.of(Role.DATA_ANALYST), Set.of(Permission.REALTIME_READ));
    }

    private static Jwt jwt(String tokenValue, String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject(subject)
                .audience(List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
