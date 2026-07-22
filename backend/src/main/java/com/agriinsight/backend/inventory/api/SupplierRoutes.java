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
public class SupplierRoutes implements SecuredRouteRegistry.Contributor {

    private static final String SUPPLIERS = ApiVersion.PREFIX + "/suppliers";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, SUPPLIERS, Permission.INVENTORY_READ),
                permission(HttpMethod.POST, SUPPLIERS, Permission.INVENTORY_MANAGE),
                permission(HttpMethod.GET, SUPPLIERS + "/{id}", Permission.INVENTORY_READ),
                permission(HttpMethod.PATCH, SUPPLIERS + "/{id}", Permission.INVENTORY_MANAGE),
                permission(
                        HttpMethod.POST,
                        SUPPLIERS + "/{id}/deactivate",
                        Permission.INVENTORY_MANAGE),
                permission(
                        HttpMethod.POST,
                        SUPPLIERS + "/{id}/reactivate",
                        Permission.INVENTORY_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
