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
public class SeasonRoutes implements SecuredRouteRegistry.Contributor {

    private static final String SEASONS = ApiVersion.PREFIX + "/seasons";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, SEASONS, Permission.SEASON_READ),
                permission(HttpMethod.GET, SEASONS + "/{id}", Permission.SEASON_READ),
                permission(HttpMethod.POST, SEASONS, Permission.SEASON_MANAGE),
                permission(HttpMethod.PATCH, SEASONS + "/{id}", Permission.SEASON_MANAGE),
                permission(HttpMethod.POST, SEASONS + "/{id}/transition", Permission.SEASON_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
