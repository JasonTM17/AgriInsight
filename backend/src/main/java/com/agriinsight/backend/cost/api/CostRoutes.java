package com.agriinsight.backend.cost.api;

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
public class CostRoutes implements SecuredRouteRegistry.Contributor {

    private static final String ENTRIES = ApiVersion.PREFIX + "/cost-entries";
    private static final String SUMMARIES = ApiVersion.PREFIX + "/cost-summaries";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, ENTRIES, Permission.COST_READ),
                permission(HttpMethod.GET, ENTRIES + "/{id}", Permission.COST_READ),
                permission(HttpMethod.GET, SUMMARIES, Permission.COST_READ),
                permission(HttpMethod.POST, ENTRIES, Permission.COST_MANAGE),
                permission(
                        HttpMethod.POST,
                        ENTRIES + "/{id}/corrections",
                        Permission.COST_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method, String pattern, Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
