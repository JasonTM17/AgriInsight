package com.agriinsight.backend.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiCommandFingerprintFactoryTest {

    @Test
    void bindsTheAuthenticatedTenantAndPrincipalToTheCanonicalRequest() {
        TenantPrincipal principal = new TestPrincipal(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000001"));

        var request = new ApiCommandFingerprintFactory().create(
                principal,
                "api-command-0001",
                "POST",
                "/api/v1/users",
                Map.of(),
                Map.of("view", List.of("summary")),
                "body-v1;",
                Map.of("If-Match", "\"7\""),
                "correlation-1");

        assertThat(request.tenantId()).isEqualTo(principal.tenantId());
        assertThat(request.principalId()).isEqualTo(principal.profileId());
        assertThat(request.fingerprint().httpMethod()).isEqualTo("POST");
        assertThat(request.idempotencyKey().toString()).doesNotContain("api-command-0001");
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }
}
