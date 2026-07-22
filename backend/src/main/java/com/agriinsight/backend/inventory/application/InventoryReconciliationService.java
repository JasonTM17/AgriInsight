package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
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
public class InventoryReconciliationService {

    private final PermissionEvaluator permissions;
    private final InventoryReconciliationStore store;

    public InventoryReconciliationService(
            PermissionEvaluator permissions,
            InventoryReconciliationStore store) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
    }

    public InventoryReconciliationReport verify() {
        ScopeContext scope = permissions.requireDomainList(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE);
        InventoryReconciliationReport report = store.reconcile(scope);
        if (!report.consistent()) {
            throw new InventoryProjectionDriftException(report);
        }
        return report;
    }
}
