package com.agriinsight.backend.authorization.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum Role {
    TENANT_ADMIN(true, EnumSet.allOf(Permission.class)),
    EXECUTIVE(true, EnumSet.of(
            Permission.FARM_READ,
            Permission.SEASON_READ,
            Permission.ACTIVITY_READ,
            Permission.HARVEST_READ,
            Permission.INVENTORY_READ,
            Permission.COST_READ)),
    FARM_MANAGER(false, EnumSet.of(
            Permission.FARM_READ,
            Permission.FARM_MANAGE,
            Permission.SEASON_READ,
            Permission.SEASON_MANAGE,
            Permission.WORKFORCE_PICKER_READ,
            Permission.ACTIVITY_READ,
            Permission.ACTIVITY_MANAGE,
            Permission.ACTIVITY_LOG_APPEND,
            Permission.HARVEST_READ,
            Permission.HARVEST_MANAGE,
            Permission.INVENTORY_READ,
            Permission.COST_READ)),
    INVENTORY_MANAGER(false, EnumSet.of(
            Permission.INVENTORY_READ,
            Permission.INVENTORY_MANAGE)),
    DATA_ANALYST(true, EnumSet.of(
            Permission.FARM_READ,
            Permission.SEASON_READ,
            Permission.ACTIVITY_READ,
            Permission.HARVEST_READ,
            Permission.INVENTORY_READ,
            Permission.COST_READ)),
    FIELD_WORKER(false, EnumSet.of(
            Permission.ACTIVITY_READ,
            Permission.ACTIVITY_LOG_APPEND)),
    SUPPLIER(false, EnumSet.noneOf(Permission.class));

    private final boolean tenantWide;
    private final Set<Permission> permissions;

    Role(boolean tenantWide, Set<Permission> permissions) {
        this.tenantWide = tenantWide;
        this.permissions = Set.copyOf(permissions);
    }

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean tenantWide() {
        return tenantWide;
    }

    public boolean grants(Permission permission) {
        return permissions.contains(Objects.requireNonNull(permission, "permission is required"));
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
