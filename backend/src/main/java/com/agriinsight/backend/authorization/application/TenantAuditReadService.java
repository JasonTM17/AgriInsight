package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantAuditReadService {

    private final PermissionEvaluator permissions;
    private final TenantAuditReadStore store;

    public TenantAuditReadService(
            PermissionEvaluator permissions, TenantAuditReadStore store) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
    }

    public TenantAuditPage list(TenantAuditQuery query) {
        ScopeContext scope = permissions.requireTenant(Permission.IDENTITY_USER_MANAGE);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }
}
