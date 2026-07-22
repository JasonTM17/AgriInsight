package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class OperatingCostQueryService {

    private final PermissionEvaluator permissions;
    private final OperatingCostQueryStore store;

    public OperatingCostQueryService(
            PermissionEvaluator permissions, OperatingCostQueryStore store) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
    }

    public OperatingCostPage list(OperatingCostQuery query) {
        return store.findAll(readScope(), Objects.requireNonNull(query, "query is required"));
    }

    public OperatingCostRecord get(UUID entryId) {
        return store.findById(readScope(), Objects.requireNonNull(entryId, "entryId is required"))
                .orElseThrow(() -> new ResourceNotFoundException("Operating cost entry"));
    }

    public CostSummaryResult summarize(CostSummaryQuery query) {
        return store.summarize(readScope(), Objects.requireNonNull(query, "query is required"));
    }

    private ScopeContext readScope() {
        return permissions.requireDomainList(Permission.COST_READ, ScopeContext.Type.FARM);
    }
}
