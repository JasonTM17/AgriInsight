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
public class WarehouseAssignmentRoutes implements SecuredRouteRegistry.Contributor {

    private static final String ASSIGNMENTS = ApiVersion.PREFIX + "/warehouse-assignments";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, ASSIGNMENTS),
                permission(HttpMethod.POST, ASSIGNMENTS),
                permission(HttpMethod.POST, ASSIGNMENTS + "/{id}/revoke"));
    }

    private static SecuredRouteRegistry.Route permission(HttpMethod method, String pattern) {
        return SecuredRouteRegistry.Route.permission(
                method, pattern, Permission.INVENTORY_ASSIGNMENT_MANAGE.name());
    }
}
