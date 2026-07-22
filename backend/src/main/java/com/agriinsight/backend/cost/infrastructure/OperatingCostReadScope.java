package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Objects;

final class OperatingCostReadScope {

    private OperatingCostReadScope() {
    }

    static ScopeContext require(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT
                && required.type() != ScopeContext.Type.FARM) {
            throw new IllegalArgumentException(
                    "Operating cost reads require tenant or farm scope");
        }
        return required;
    }
}
