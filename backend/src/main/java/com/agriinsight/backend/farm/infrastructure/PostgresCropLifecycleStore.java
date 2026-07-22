package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.CropRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresCropLifecycleStore {

    private static final String LIVE_SEASON_PREDICATE = """
            EXISTS (
                SELECT 1 FROM seasons AS season
                 WHERE season.tenant_id = crop.tenant_id
                   AND season.crop_id = crop.id
                   AND season.status IN ('PLANNED', 'ACTIVE'))
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresCropLifecycleStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<CropRecord> updateActive(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            boolean active) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID requiredCropId = Objects.requireNonNull(cropId, "cropId is required");
        requireVersion(expectedVersion);
        if (!lockCrop(tenantScope, requiredCropId)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE crops AS crop
                   SET active = ?, version = crop.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE crop.tenant_id = ?
                   AND crop.id = ?
                   AND crop.version = ?
                   AND crop.active <> ?
                """);
        if (!active) {
            sql.append(" AND NOT (").append(LIVE_SEASON_PREDICATE).append(')');
        }
        sql.append(" RETURNING ").append(CropRowMapping.RETURNING_COLUMNS);
        return CropRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), CropRowMapping.MAPPER,
                active, tenantScope.tenantId(), requiredCropId, expectedVersion, active));
    }

    boolean hasDeactivationBlockers(ScopeContext scope, UUID cropId) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID requiredCropId = Objects.requireNonNull(cropId, "cropId is required");
        String sql = "SELECT (" + LIVE_SEASON_PREDICATE + ") AS blocked "
                + "FROM crops AS crop WHERE crop.tenant_id = ? AND crop.id = ?";
        return CropRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getBoolean("blocked"),
                tenantScope.tenantId(), requiredCropId)).orElse(false);
    }

    private boolean lockCrop(ScopeContext scope, UUID cropId) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT crop.id FROM crops AS crop
                 WHERE crop.tenant_id = ? AND crop.id = ?
                 FOR UPDATE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), cropId);
        return CropRowMapping.exactlyOneOrEmpty(rows).isPresent();
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Crop lifecycle requires tenant-wide scope");
        }
        return required;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
