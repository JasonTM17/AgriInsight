package com.agriinsight.backend.shared.persistence;

import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("!test")
public class TenantContextBinder {

    private static final String SET_TENANT_CONTEXT =
            "SELECT set_config('app.tenant_id', ?, true)";

    private final JdbcTemplate jdbcTemplate;

    public TenantContextBinder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    public void bind(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TenantContextRequiredException("Tenant context requires an active transaction");
        }
        String configured = jdbcTemplate.queryForObject(
                SET_TENANT_CONTEXT,
                String.class,
                tenantId.toString());
        if (!tenantId.toString().equals(configured)) {
            throw new TenantContextRequiredException("Tenant context could not be established");
        }
    }
}
