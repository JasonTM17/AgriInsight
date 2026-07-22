package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Objects;

final class OperatingCostStoreScope {

    private OperatingCostStoreScope() {
    }

    static ScopeContext requireTenant(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT
                || required.resourceId().isPresent()) {
            throw new IllegalArgumentException(
                    "Operating cost writes require tenant-wide management scope");
        }
        return required;
    }
}
