package com.agriinsight.backend.realtime.api;

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
public class RealtimeRoutes implements SecuredRouteRegistry.Contributor {

    private static final String SUMMARY = ApiVersion.PREFIX + "/realtime/summary";
    private static final String ALERTS = ApiVersion.PREFIX + "/realtime/alerts";
    private static final String ACKNOWLEDGEMENTS = ALERTS + "/{id}/acknowledgements";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, SUMMARY, Permission.REALTIME_READ),
                permission(HttpMethod.GET, ALERTS, Permission.REALTIME_ALERT_READ),
                permission(
                        HttpMethod.POST,
                        ACKNOWLEDGEMENTS,
                        Permission.REALTIME_ALERT_ACKNOWLEDGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
