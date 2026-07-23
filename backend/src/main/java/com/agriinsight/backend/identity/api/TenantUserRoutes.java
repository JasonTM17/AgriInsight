package com.agriinsight.backend.identity.api;

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
public class TenantUserRoutes implements SecuredRouteRegistry.Contributor {

    private static final String USERS = ApiVersion.PREFIX + "/users";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, USERS, Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.POST, USERS, Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.GET, USERS + "/{id}", Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.POST, USERS + "/{id}/deactivate", Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.POST, USERS + "/{id}/reactivate", Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.GET, USERS + "/{id}/external-identities", Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.POST, USERS + "/{id}/external-identities", Permission.IDENTITY_USER_MANAGE),
                permission(HttpMethod.POST,
                        USERS + "/{id}/external-identities/{identityId}/unlink",
                        Permission.IDENTITY_USER_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method,
            String pattern,
            Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
