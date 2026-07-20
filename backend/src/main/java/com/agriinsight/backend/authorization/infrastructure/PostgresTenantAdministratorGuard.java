package com.agriinsight.backend.authorization.infrastructure;

import com.agriinsight.backend.authorization.application.TenantAdministratorGuard;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresTenantAdministratorGuard implements TenantAdministratorGuard {

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantAdministratorGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public void assertPathRemains(ScopeContext scope, UUID profileId) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        Boolean invoked = jdbcTemplate.queryForObject("""
                SELECT agriinsight_security.assert_admin_path_remains(?, NULL, TRUE)
                """, (result, rowNumber) -> Boolean.TRUE, profileId);
        if (!Boolean.TRUE.equals(invoked)) {
            throw new IllegalStateException("Tenant administrator guard was not evaluated");
        }
    }

    private void requireTenantScope(ScopeContext scope) {
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Administrator guard requires tenant-wide scope");
        }
    }
}
