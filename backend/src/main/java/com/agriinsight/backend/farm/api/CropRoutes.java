package com.agriinsight.backend.farm.api;

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
public class CropRoutes implements SecuredRouteRegistry.Contributor {

    private static final String CROPS = ApiVersion.PREFIX + "/crops";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, CROPS, Permission.FARM_READ),
                permission(HttpMethod.GET, CROPS + "/{id}", Permission.FARM_READ),
                permission(HttpMethod.POST, CROPS, Permission.FARM_MANAGE),
                permission(HttpMethod.PATCH, CROPS + "/{id}", Permission.FARM_MANAGE),
                permission(HttpMethod.POST, CROPS + "/{id}/deactivate", Permission.FARM_MANAGE),
                permission(HttpMethod.POST, CROPS + "/{id}/reactivate", Permission.FARM_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
