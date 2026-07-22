package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FarmAssignmentRecord;
import com.agriinsight.backend.farm.application.FarmAssignmentStore;
import com.agriinsight.backend.farm.domain.FarmAssignment;
import java.sql.Timestamp;
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
public class PostgresFarmAssignmentStore implements FarmAssignmentStore {

    private static final String COLUMNS = """
            id, tenant_id, user_profile_id, farm_id, revoked_at, version
            """;
    private static final RowMapper<FarmAssignmentRecord> MAPPER = (result, rowNumber) -> {
        Timestamp revokedAt = result.getTimestamp("revoked_at");
        return new FarmAssignmentRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("user_profile_id", UUID.class),
                result.getObject("farm_id", UUID.class),
                revokedAt == null ? Optional.empty() : Optional.of(revokedAt.toInstant()),
                result.getLong("version"));
    };

    private final JdbcTemplate jdbcTemplate;

    public PostgresFarmAssignmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public Optional<FarmAssignmentRecord> findById(
            ScopeContext scope,
            UUID assignmentId) {
        requireTenantScope(scope);
        Objects.requireNonNull(assignmentId, "assignmentId is required");
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_farm_assignments WHERE tenant_id = ? AND id = ?",
                MAPPER,
                scope.tenantId(),
                assignmentId));
    }

    @Override
    public Optional<FarmAssignmentRecord> findActive(
            ScopeContext scope,
            UUID userProfileId,
            UUID farmId) {
        requireTenantScope(scope);
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + COLUMNS + """
                         FROM user_farm_assignments
                        WHERE tenant_id = ?
                          AND user_profile_id = ?
                          AND farm_id = ?
                          AND revoked_at IS NULL
                        """,
                MAPPER,
                scope.tenantId(),
                userProfileId,
                farmId));
    }

    @Override
    public boolean activeProfileExists(ScopeContext scope, UUID userProfileId) {
        requireTenantScope(scope);
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        return count("""
                SELECT count(*) FROM user_profiles
                 WHERE tenant_id = ? AND id = ? AND active
                """, scope.tenantId(), userProfileId) == 1;
    }

    @Override
    public boolean activeFarmExists(ScopeContext scope, UUID farmId) {
        requireTenantScope(scope);
        Objects.requireNonNull(farmId, "farmId is required");
        return count("""
                SELECT count(*) FROM farms
                 WHERE tenant_id = ? AND id = ? AND active
                """, scope.tenantId(), farmId) == 1;
    }

    @Override
    public FarmAssignmentRecord create(ScopeContext scope, FarmAssignment assignment) {
        requireTenantScope(scope);
        Objects.requireNonNull(assignment, "assignment is required");
        if (!scope.tenantId().equals(assignment.tenantId())) {
            throw new IllegalArgumentException("Farm assignment cannot switch tenants");
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                VALUES (?, ?, ?, ?)
                RETURNING id, tenant_id, user_profile_id, farm_id, revoked_at, version
                """,
                MAPPER,
                assignment.id(),
                assignment.tenantId(),
                assignment.userProfileId(),
                assignment.farmId())).orElseThrow(() ->
                        new IllegalStateException("Farm assignment was not created"));
    }

    @Override
    public Optional<FarmAssignmentRecord> revoke(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion) {
        requireTenantScope(scope);
        Objects.requireNonNull(assignmentId, "assignmentId is required");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                UPDATE user_farm_assignments
                   SET revoked_at = CURRENT_TIMESTAMP,
                       version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ?
                   AND id = ?
                   AND version = ?
                   AND revoked_at IS NULL
                RETURNING id, tenant_id, user_profile_id, farm_id, revoked_at, version
                """,
                MAPPER,
                scope.tenantId(),
                assignmentId,
                expectedVersion));
    }

    private long count(String sql, Object... parameters) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return result == null ? 0 : result;
    }

    private void requireTenantScope(ScopeContext scope) {
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Farm assignment store requires tenant-wide scope");
        }
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Farm assignment query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
