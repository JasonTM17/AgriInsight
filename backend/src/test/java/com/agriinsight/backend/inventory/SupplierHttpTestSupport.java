package com.agriinsight.backend.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.inventory.application.SupplierRecord;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class SupplierHttpTestSupport {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID SUPPLIER_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    static final UUID COMMAND_ID = UUID.fromString("53000000-0000-0000-0000-000000000003");
    static final String TOKEN = "supplier-api-token";
    static final String AUTHORIZATION = "Bearer " + TOKEN;

    private SupplierHttpTestSupport() {
    }

    static void stubIdentity(
            JwtDecoder decoder,
            TenantPrincipalLoader principalLoader,
            Set<Permission> permissions) {
        when(decoder.decode(TOKEN)).thenReturn(jwt());
        when(principalLoader.load(any())).thenReturn(new AgriInsightPrincipal(
                ACTOR_ID,
                TENANT_ID,
                "TENANT-A",
                Optional.of("Inventory Admin"),
                Optional.empty(),
                Optional.of("mfa"),
                Set.of(Role.TENANT_ADMIN),
                permissions));
    }

    static SupplierRecord supplier(long version) {
        return supplier(version, true);
    }

    static SupplierRecord supplier(long version, boolean active) {
        return new SupplierRecord(
                SUPPLIER_ID,
                TENANT_ID,
                "SUP-A",
                "Supplier A",
                active,
                version);
    }

    static CommandExecutionResult.Completed<SupplierRecord> completed(
            int status,
            SupplierRecord supplier) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                false,
                status,
                new CommandTarget("SUPPLIER", supplier.id(), supplier.version()),
                Optional.of(supplier));
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://identity.example.test/issuer")
                .subject("inventory-admin")
                .audience(java.util.List.of("agriinsight-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(90))
                .claim("token_use", "access")
                .build();
    }
}
