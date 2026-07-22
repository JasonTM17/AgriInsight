package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.domain.CostTarget;
import java.util.Locale;
import java.util.UUID;

final class CostApiRequestSupport {

    private CostApiRequestSupport() {
    }

    static CostTarget target(CostTarget.Type type, UUID targetId) {
        if (type == CostTarget.Type.TENANT) {
            if (targetId != null) {
                throw new IllegalArgumentException("TENANT target must omit targetId");
            }
            return CostTarget.tenant();
        }
        if (targetId == null) {
            throw new IllegalArgumentException("Domain cost target requires targetId");
        }
        return CostTarget.domain(type, targetId);
    }

    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String reasonCode(String value) {
        String normalized = optionalText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
