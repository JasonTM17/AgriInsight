package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FarmPage;
import com.agriinsight.backend.farm.application.FarmQuery;
import com.agriinsight.backend.farm.application.FarmRecord;
import com.agriinsight.backend.farm.application.FarmStore;
import com.agriinsight.backend.farm.domain.Farm;
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
public class PostgresFarmStore implements FarmStore {

    private static final String COLUMNS = "id, tenant_id, code, display_name, active, version";
    private static final String SELECT_COLUMNS = """
            farm.id, farm.tenant_id, farm.code, farm.display_name, farm.active, farm.version
            """;
    private static final RowMapper<FarmRecord> MAPPER = (result, rowNumber) -> new FarmRecord(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getString("code"),
            result.getString("display_name"),
            result.getBoolean("active"),
            result.getLong("version"));

    private final JdbcTemplate jdbcTemplate;

    public PostgresFarmStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public FarmPage findAll(ScopeContext scope, FarmQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(SELECT_COLUMNS)
                .append(" FROM farms farm WHERE farm.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        FarmScopeSql.append(sql, parameters, scope, null);
        query.active().ifPresent(active -> {
            sql.append(" AND farm.active = ?");
            parameters.add(active);
        });
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(farm.code)) > 0")
                    .append(" OR position(lower(?) in lower(farm.display_name)) > 0)");
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(farm.display_name), farm.code, farm.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());

        List<FarmRecord> rows = jdbcTemplate.query(sql.toString(), MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<FarmRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new FarmPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<FarmRecord> findById(ScopeContext scope, UUID farmId) {
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(SELECT_COLUMNS)
                .append(" FROM farms farm WHERE farm.tenant_id = ? AND farm.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        parameters.add(requiredFarmId);
        FarmScopeSql.append(sql, parameters, scope, requiredFarmId);
        return exactlyOneOrEmpty(jdbcTemplate.query(sql.toString(), MAPPER, parameters.toArray()));
    }

    @Override
    public FarmRecord create(ScopeContext scope, Farm farm) {
        requireTenantScope(scope);
        Objects.requireNonNull(farm, "farm is required");
        if (!scope.tenantId().equals(farm.tenantId())) {
            throw new IllegalArgumentException("Farm cannot switch tenants");
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO farms (id, tenant_id, code, display_name)
                VALUES (?, ?, ?, ?)
                RETURNING id, tenant_id, code, display_name, active, version
                """,
                MAPPER,
                farm.id(),
                farm.tenantId(),
                farm.code(),
                farm.displayName())).orElseThrow(() -> new IllegalStateException("Farm was not created"));
    }

    @Override
    public Optional<FarmRecord> update(
            ScopeContext scope,
            UUID farmId,
            long expectedVersion,
            Optional<String> code,
            Optional<String> displayName) {
        requireVersion(expectedVersion);
        String newCode = Objects.requireNonNull(code, "code is required").orElse(null);
        String newDisplayName = Objects.requireNonNull(displayName, "displayName is required").orElse(null);
        if (newCode == null && newDisplayName == null) {
            throw new IllegalArgumentException("At least one farm field must be provided");
        }
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        StringBuilder sql = new StringBuilder("""
                UPDATE farms AS farm
                   SET code = COALESCE(CAST(? AS VARCHAR), farm.code),
                       display_name = COALESCE(CAST(? AS VARCHAR), farm.display_name),
                       version = farm.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.version = ?
                   AND ((CAST(? AS VARCHAR) IS NOT NULL AND farm.code IS DISTINCT FROM CAST(? AS VARCHAR))
                     OR (CAST(? AS VARCHAR) IS NOT NULL AND farm.display_name IS DISTINCT FROM CAST(? AS VARCHAR)))
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                optionalParameter(newCode),
                optionalParameter(newDisplayName),
                requireScope(scope).tenantId(),
                requiredFarmId,
                expectedVersion,
                optionalParameter(newCode),
                optionalParameter(newCode),
                optionalParameter(newDisplayName),
                optionalParameter(newDisplayName)));
        FarmScopeSql.append(sql, parameters, scope, requiredFarmId);
        sql.append(" RETURNING ").append(COLUMNS);
        return exactlyOneOrEmpty(jdbcTemplate.query(sql.toString(), MAPPER, parameters.toArray()));
    }

    @Override
    public Optional<FarmRecord> updateActive(
            ScopeContext scope,
            UUID farmId,
            long expectedVersion,
            boolean active) {
        requireVersion(expectedVersion);
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        StringBuilder sql = new StringBuilder("""
                UPDATE farms AS farm
                   SET active = ?,
                       version = farm.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.version = ?
                   AND farm.active <> ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                active, requireScope(scope).tenantId(), requiredFarmId, expectedVersion, active));
        FarmScopeSql.append(sql, parameters, scope, requiredFarmId);
        sql.append(" RETURNING ").append(COLUMNS);
        return exactlyOneOrEmpty(jdbcTemplate.query(sql.toString(), MAPPER, parameters.toArray()));
    }

    private ScopeContext requireScope(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }

    private void requireTenantScope(ScopeContext scope) {
        requireScope(scope);
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Farm creation requires tenant-wide scope");
        }
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private Object optionalParameter(String value) {
        return value == null ? new org.springframework.jdbc.core.SqlParameterValue(
                java.sql.Types.VARCHAR, null) : value;
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Farm query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
