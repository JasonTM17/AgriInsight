package com.agriinsight.backend.authorization.api;

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
public class TenantAuditRoutes implements SecuredRouteRegistry.Contributor {

    private static final String AUDIT_EVENTS = ApiVersion.PREFIX + "/audit-events";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(SecuredRouteRegistry.Route.permission(
                HttpMethod.GET,
                AUDIT_EVENTS,
                Permission.IDENTITY_USER_MANAGE.name()));
    }
}
