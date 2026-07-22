package com.agriinsight.backend.operations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.operations.application.EmployeeRecord;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class EmployeeHttpTestSupport {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID EMPLOYEE_ID = UUID.fromString("36000000-0000-0000-0000-000000000001");
    static final UUID COMMAND_ID = UUID.fromString("37000000-0000-0000-0000-000000000001");
    static final String TOKEN = "employee-api-token";
    static final String AUTHORIZATION = "Bearer " + TOKEN;

    private EmployeeHttpTestSupport() {
    }

    static void stubIdentity(
            JwtDecoder decoder,
            TenantPrincipalLoader principalLoader,
            Set<Permission> permissions) {
        when(decoder.decode(TOKEN)).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID, TENANT_ID, "TENANT-A", Optional.of("Tenant Admin"),
                Optional.empty(), Optional.of("mfa"), Set.of(Role.TENANT_ADMIN), permissions));
    }

    static EmployeeRecord employee(long version) {
        return employee(version, true);
    }

    static EmployeeRecord employee(long version, boolean active) {
        return new EmployeeRecord(
                EMPLOYEE_ID, TENANT_ID, "WORKER-A", "Worker A",
                Optional.of("Technician"), active, version);
    }

    static CommandExecutionResult.Completed<EmployeeRecord> completed(
            int status, EmployeeRecord employee) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID, false, status,
                new CommandTarget("EMPLOYEE", employee.id(), employee.version()),
                Optional.of(employee));
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("provider-admin")
                .audience(java.util.List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
