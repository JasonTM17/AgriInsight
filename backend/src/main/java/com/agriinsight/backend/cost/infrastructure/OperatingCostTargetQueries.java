package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.cost.domain.CostTarget;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

final class OperatingCostTargetQueries {

    private final JdbcTemplate jdbcTemplate;

    OperatingCostTargetQueries(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "jdbcTemplate is required");
    }

    boolean available(ScopeContext scope, CostTarget target) {
        ScopeContext required = OperatingCostStoreScope.requireTenant(scope);
        CostTarget value = Objects.requireNonNull(target, "target is required");
        if (value.type() == CostTarget.Type.TENANT) {
            return exists(
                    "SELECT id FROM tenants WHERE id = ? AND active",
                    required.tenantId());
        }
        String table = switch (value.type()) {
            case FARM -> "farms";
            case FIELD -> "fields";
            case SEASON -> "seasons";
            case ACTIVITY -> "activities";
            case TENANT -> throw new IllegalStateException("Tenant target handled above");
        };
        return exists(
                "SELECT id FROM " + table + " WHERE tenant_id = ? AND id = ?",
                required.tenantId(), value.id().orElseThrow());
    }

    private boolean exists(String sql, Object... parameters) {
        return !jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getObject("id"),
                parameters).isEmpty();
    }
}
