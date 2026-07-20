package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.authorization.application.TenantRoleAssignment;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentStore;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresTenantRoleAssignmentStore implements TenantRoleAssignmentStore {

    private static final RowMapper<TenantRoleAssignment> ASSIGNMENT_MAPPER = (result, rowNumber) ->
            new TenantRoleAssignment(
                    result.getObject("id", UUID.class),
                    result.getObject("tenant_id", UUID.class),
                    result.getObject("user_profile_id", UUID.class),
                    Role.valueOf(result.getString("role_code")),
                    result.getObject("revoked_at") == null,
                    result.getLong("version"));
    private static final String ASSIGNMENT_COLUMNS = """
            id, tenant_id, user_profile_id, role_code, revoked_at, version
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantRoleAssignmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public boolean profileExists(ScopeContext scope, UUID profileId) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        List<Boolean> rows = jdbcTemplate.query(
                """
                SELECT TRUE
                FROM user_profiles
                WHERE tenant_id = ? AND id = ?
                """,
                (result, rowNumber) -> Boolean.TRUE,
                scope.tenantId(),
                profileId);
        return exactlyOneOrEmpty(rows).orElse(false);
    }

    @Override
    public Optional<TenantRoleAssignment> find(
            ScopeContext scope,
            UUID profileId,
            Role role) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(role, "role is required");
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + ASSIGNMENT_COLUMNS + " FROM user_roles"
                        + " WHERE tenant_id = ? AND user_profile_id = ? AND role_code = ?",
                ASSIGNMENT_MAPPER,
                scope.tenantId(),
                profileId,
                role.name()));
    }

    @Override
    public Optional<TenantRoleAssignment> create(
            ScopeContext scope,
            UUID assignmentId,
            UUID profileId,
            Role role) {
        requireTenantScope(scope);
        Objects.requireNonNull(assignmentId, "assignmentId is required");
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(role, "role is required");
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                SELECT ?, ?, profile.id, ?
                FROM user_profiles AS profile
                WHERE profile.tenant_id = ? AND profile.id = ?
                ON CONFLICT (tenant_id, user_profile_id, role_code) DO NOTHING
                RETURNING id, tenant_id, user_profile_id, role_code, revoked_at, version
                """,
                ASSIGNMENT_MAPPER,
                assignmentId,
                scope.tenantId(),
                role.name(),
                scope.tenantId(),
                profileId));
    }

    @Override
    public Optional<TenantRoleAssignment> reactivate(
            ScopeContext scope,
            UUID profileId,
            Role role,
            long expectedVersion) {
        return updateActive(scope, profileId, role, expectedVersion, true);
    }

    @Override
    public Optional<TenantRoleAssignment> revoke(
            ScopeContext scope,
            UUID profileId,
            Role role,
            long expectedVersion) {
        return updateActive(scope, profileId, role, expectedVersion, false);
    }

    private Optional<TenantRoleAssignment> updateActive(
            ScopeContext scope,
            UUID profileId,
            Role role,
            long expectedVersion,
            boolean active) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(role, "role is required");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                UPDATE user_roles
                   SET revoked_at = CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END,
                       version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ?
                   AND user_profile_id = ?
                   AND role_code = ?
                   AND version = ?
                   AND ((? AND revoked_at IS NOT NULL) OR (NOT ? AND revoked_at IS NULL))
                RETURNING id, tenant_id, user_profile_id, role_code, revoked_at, version
                """,
                ASSIGNMENT_MAPPER,
                active,
                scope.tenantId(),
                profileId,
                role.name(),
                expectedVersion,
                active,
                active));
    }

    private void requireTenantScope(ScopeContext scope) {
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Role assignment store requires tenant-wide scope");
        }
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Role assignment query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
