package com.agriinsight.backend.operations.api;

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
public class HarvestRoutes implements SecuredRouteRegistry.Contributor {

    private static final String HARVESTS = ApiVersion.PREFIX + "/harvests";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                route(HttpMethod.GET, HARVESTS, Permission.HARVEST_READ),
                route(HttpMethod.GET, HARVESTS + "/{id}", Permission.HARVEST_READ),
                route(HttpMethod.POST, HARVESTS, Permission.HARVEST_MANAGE),
                route(HttpMethod.POST, HARVESTS + "/{id}/corrections", Permission.HARVEST_MANAGE));
    }

    private SecuredRouteRegistry.Route route(
            HttpMethod method, String pattern, Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
