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
public class TenantRoleRoutes implements SecuredRouteRegistry.Contributor {

    private static final String ROLES = ApiVersion.PREFIX + "/users/{id}/roles";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                SecuredRouteRegistry.Route.permission(
                        HttpMethod.POST,
                        ROLES,
                        Permission.IDENTITY_ROLE_MANAGE.name()),
                SecuredRouteRegistry.Route.permission(
                        HttpMethod.POST,
                        ROLES + "/{roleCode}/revoke",
                        Permission.IDENTITY_ROLE_MANAGE.name()));
    }
}
