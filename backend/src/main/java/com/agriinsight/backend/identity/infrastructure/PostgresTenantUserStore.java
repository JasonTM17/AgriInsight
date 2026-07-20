package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.identity.application.TenantUserPage;
import com.agriinsight.backend.identity.application.TenantUserProfile;
import com.agriinsight.backend.identity.application.TenantUserQuery;
import com.agriinsight.backend.identity.application.TenantUserStore;
import com.agriinsight.backend.identity.domain.UserProfile;
import java.util.ArrayList;
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
public class PostgresTenantUserStore implements TenantUserStore {

    private static final String PROFILE_COLUMNS = """
            id, tenant_id, display_name, email, active, version
            """;
    private static final RowMapper<TenantUserProfile> PROFILE_MAPPER = (result, rowNumber) ->
            new TenantUserProfile(
                    result.getObject("id", UUID.class),
                    result.getObject("tenant_id", UUID.class),
                    result.getString("display_name"),
                    Optional.ofNullable(result.getString("email")),
                    result.getBoolean("active"),
                    result.getLong("version"));

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantUserStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public TenantUserPage findAll(ScopeContext scope, TenantUserQuery query) {
        requireTenantScope(scope);
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(PROFILE_COLUMNS)
                .append(" FROM user_profiles WHERE tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(scope.tenantId());
        query.active().ifPresent(active -> {
            sql.append(" AND active = ?");
            parameters.add(active);
        });
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(display_name)) > 0")
                    .append(" OR position(lower(?) in lower(COALESCE(email, ''))) > 0)");
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(display_name), id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());

        List<TenantUserProfile> rows = jdbcTemplate.query(
                sql.toString(),
                PROFILE_MAPPER,
                parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<TenantUserProfile> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new TenantUserPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<TenantUserProfile> findById(ScopeContext scope, UUID profileId) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + PROFILE_COLUMNS + " FROM user_profiles WHERE tenant_id = ? AND id = ?",
                PROFILE_MAPPER,
                scope.tenantId(),
                profileId));
    }

    @Override
    public TenantUserProfile create(ScopeContext scope, UserProfile profile) {
        requireTenantScope(scope);
        Objects.requireNonNull(profile, "profile is required");
        requireSameTenant(scope, profile.getTenantId());
        List<TenantUserProfile> rows = jdbcTemplate.query("""
                INSERT INTO user_profiles (id, tenant_id, display_name, email)
                VALUES (?, ?, ?, ?)
                RETURNING id, tenant_id, display_name, email, active, version
                """,
                PROFILE_MAPPER,
                profile.getId(),
                profile.getTenantId(),
                profile.getDisplayName(),
                profile.getEmail());
        return exactlyOneOrEmpty(rows)
                .orElseThrow(() -> new IllegalStateException("Tenant user was not created"));
    }

    @Override
    public Optional<TenantUserProfile> updateActive(
            ScopeContext scope,
            UUID profileId,
            long expectedVersion,
            boolean active) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                UPDATE user_profiles
                   SET active = ?,
                       version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ?
                   AND id = ?
                   AND version = ?
                   AND active <> ?
                RETURNING id, tenant_id, display_name, email, active, version
                """,
                PROFILE_MAPPER,
                active,
                scope.tenantId(),
                profileId,
                expectedVersion,
                active));
    }

    private void requireTenantScope(ScopeContext scope) {
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Tenant user store requires tenant-wide scope");
        }
    }

    private void requireSameTenant(ScopeContext scope, UUID tenantId) {
        if (!scope.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant-scoped data cannot switch tenants");
        }
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Tenant user query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
