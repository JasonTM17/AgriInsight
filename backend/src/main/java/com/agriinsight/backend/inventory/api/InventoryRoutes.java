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
public class InventoryRoutes implements SecuredRouteRegistry.Contributor {

    private static final String INVENTORY = ApiVersion.PREFIX + "/inventory";
    private static final String TRANSACTIONS = INVENTORY + "/transactions";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, INVENTORY + "/balances", Permission.INVENTORY_READ),
                permission(HttpMethod.GET, INVENTORY + "/lots", Permission.INVENTORY_READ),
                permission(HttpMethod.GET, TRANSACTIONS, Permission.INVENTORY_READ),
                permission(HttpMethod.GET, TRANSACTIONS + "/{id}", Permission.INVENTORY_READ),
                permission(HttpMethod.POST, TRANSACTIONS, Permission.INVENTORY_MANAGE),
                permission(
                        HttpMethod.POST,
                        TRANSACTIONS + "/{id}/reversals",
                        Permission.INVENTORY_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
