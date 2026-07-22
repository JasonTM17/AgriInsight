package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class WarehouseRoutes implements SecuredRouteRegistry.Contributor {

    private static final String WAREHOUSES = ApiVersion.PREFIX + "/warehouses";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, WAREHOUSES, Permission.INVENTORY_READ),
                permission(HttpMethod.POST, WAREHOUSES, Permission.INVENTORY_MANAGE),
                permission(HttpMethod.GET, WAREHOUSES + "/{id}", Permission.INVENTORY_READ),
                permission(HttpMethod.PATCH, WAREHOUSES + "/{id}", Permission.INVENTORY_MANAGE),
                permission(
                        HttpMethod.POST,
                        WAREHOUSES + "/{id}/deactivate",
                        Permission.INVENTORY_MANAGE),
                permission(
                        HttpMethod.POST,
                        WAREHOUSES + "/{id}/reactivate",
                        Permission.INVENTORY_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
