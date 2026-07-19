package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.IdentityRejectedException;
import com.agriinsight.backend.identity.application.IdentityRejectionReason;
import com.agriinsight.backend.identity.application.TenantPrincipalData;
import com.agriinsight.backend.identity.application.TenantPrincipalPort;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresTenantPrincipalRepository implements TenantPrincipalPort {

    private static final String LOAD_PRINCIPAL = """
            SELECT
                profile.id AS profile_id,
                profile.tenant_id,
                tenant.code AS tenant_code,
                profile.display_name,
                profile.email,
                assignment.role_code,
                grant_row.permission_code
            FROM user_profiles AS profile
            JOIN tenants AS tenant
              ON tenant.id = profile.tenant_id
             AND tenant.active = TRUE
            LEFT JOIN user_roles AS assignment
              ON assignment.tenant_id = profile.tenant_id
             AND assignment.user_profile_id = profile.id
             AND assignment.revoked_at IS NULL
            LEFT JOIN role_permissions AS grant_row
              ON grant_row.role_code = assignment.role_code
            WHERE profile.id = ?
              AND profile.tenant_id = ?
              AND profile.active = TRUE
            ORDER BY assignment.role_code, grant_row.permission_code
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantPrincipalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public Optional<TenantPrincipalData> findActiveByProfileAndTenant(UUID profileId, UUID tenantId) {
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        List<PrincipalRow> rows = jdbcTemplate.query(
                LOAD_PRINCIPAL,
                (result, rowNumber) -> new PrincipalRow(
                        result.getObject("profile_id", UUID.class),
                        result.getObject("tenant_id", UUID.class),
                        result.getString("tenant_code"),
                        result.getString("display_name"),
                        result.getString("email"),
                        result.getString("role_code"),
                        result.getString("permission_code")),
                profileId,
                tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        PrincipalRow first = rows.getFirst();
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        try {
            for (PrincipalRow row : rows) {
                if (row.roleCode() != null) {
                    roles.add(Role.valueOf(row.roleCode()));
                }
                if (row.permissionCode() != null) {
                    permissions.add(Permission.valueOf(row.permissionCode()));
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new IdentityRejectedException(IdentityRejectionReason.INVALID_AUTHORIZATION_STATE);
        }
        return Optional.of(new TenantPrincipalData(
                first.profileId(),
                first.tenantId(),
                first.tenantCode(),
                first.displayName(),
                Optional.ofNullable(first.email()),
                roles,
                permissions));
    }

    private record PrincipalRow(
            UUID profileId,
            UUID tenantId,
            String tenantCode,
            String displayName,
            String email,
            String roleCode,
            String permissionCode) {
    }
}
