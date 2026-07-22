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
public class ActivityRoutes implements SecuredRouteRegistry.Contributor {

    private static final String ACTIVITIES = ApiVersion.PREFIX + "/activities";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, ACTIVITIES, Permission.ACTIVITY_READ),
                permission(HttpMethod.GET, ACTIVITIES + "/{id}", Permission.ACTIVITY_READ),
                permission(HttpMethod.POST, ACTIVITIES, Permission.ACTIVITY_MANAGE),
                permission(HttpMethod.PATCH, ACTIVITIES + "/{id}", Permission.ACTIVITY_MANAGE),
                permission(HttpMethod.POST, ACTIVITIES + "/{id}/transition", Permission.ACTIVITY_MANAGE),
                permission(HttpMethod.POST, ACTIVITIES + "/{id}/assignments", Permission.ACTIVITY_MANAGE),
                permission(HttpMethod.POST, ACTIVITIES + "/{id}/assignments/{assignmentId}/revoke",
                        Permission.ACTIVITY_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
