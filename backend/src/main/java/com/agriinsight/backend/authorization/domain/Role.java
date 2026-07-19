package com.agriinsight.backend.authorization.domain;

public enum Role {
    TENANT_ADMIN,
    EXECUTIVE,
    FARM_MANAGER,
    INVENTORY_MANAGER,
    DATA_ANALYST,
    FIELD_WORKER,
    SUPPLIER;

    public String authority() {
        return "ROLE_" + name();
    }
}
