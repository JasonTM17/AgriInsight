package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.CropCommands;
import com.agriinsight.backend.farm.application.CropPage;
import com.agriinsight.backend.farm.application.CropQuery;
import com.agriinsight.backend.farm.application.CropRecord;
import com.agriinsight.backend.farm.application.CropStore;
import com.agriinsight.backend.farm.domain.Crop;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresCropStore implements CropStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresCropMutationStore mutations;
    private final PostgresCropLifecycleStore lifecycle;

    public PostgresCropStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresCropMutationStore(jdbcTemplate);
        this.lifecycle = new PostgresCropLifecycleStore(jdbcTemplate);
    }

    @Override
    public CropPage findAll(ScopeContext scope, CropQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE crop.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        CropScopeSql.append(sql, parameters, scope);
        query.active().ifPresent(active -> addFilter(sql, parameters, "crop.active", active));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(crop.code)) > 0")
                    .append(" OR position(lower(?) in lower(crop.display_name)) > 0")
                    .append(" OR position(lower(?) in lower(COALESCE(crop.scientific_name, ''))) > 0)");
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(crop.display_name), crop.code, crop.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<CropRecord> rows = jdbcTemplate.query(
                sql.toString(), CropRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<CropRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new CropPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<CropRecord> findById(ScopeContext scope, UUID cropId) {
        StringBuilder sql = baseSelect()
                .append(" WHERE crop.tenant_id = ? AND crop.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        parameters.add(Objects.requireNonNull(cropId, "cropId is required"));
        CropScopeSql.append(sql, parameters, scope);
        return CropRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), CropRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public CropRecord create(ScopeContext scope, Crop crop) {
        requireTenantScope(scope);
        Objects.requireNonNull(crop, "crop is required");
        if (!scope.tenantId().equals(crop.tenantId())) {
            throw new IllegalArgumentException("Crop cannot switch tenants");
        }
        return CropRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO crops (id, tenant_id, code, display_name, scientific_name)
                VALUES (?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(CropRowMapping.RETURNING_COLUMNS),
                CropRowMapping.MAPPER,
                crop.id(), crop.tenantId(), crop.code(), crop.displayName(),
                nullable(crop.scientificName().orElse(null))))
                .orElseThrow(() -> new IllegalStateException("Crop was not created"));
    }

    @Override
    public Optional<CropRecord> update(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            CropCommands.Update command) {
        return mutations.update(scope, cropId, expectedVersion, command);
    }

    @Override
    public Optional<CropRecord> updateActive(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            boolean active) {
        return lifecycle.updateActive(scope, cropId, expectedVersion, active);
    }

    @Override
    public boolean hasDeactivationBlockers(ScopeContext scope, UUID cropId) {
        return lifecycle.hasDeactivationBlockers(scope, cropId);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(CropRowMapping.SELECT_COLUMNS)
                .append(" FROM crops AS crop");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }

    private ScopeContext requireScope(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }

    private void requireTenantScope(ScopeContext scope) {
        ScopeContext required = requireScope(scope);
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Crop mutation requires tenant-wide scope");
        }
    }
}
