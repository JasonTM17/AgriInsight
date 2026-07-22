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
public class EmployeeRoutes implements SecuredRouteRegistry.Contributor {

    private static final String EMPLOYEES = ApiVersion.PREFIX + "/employees";

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(
                permission(HttpMethod.GET, EMPLOYEES + "/eligible", Permission.WORKFORCE_PICKER_READ),
                permission(HttpMethod.GET, EMPLOYEES, Permission.WORKFORCE_MANAGE),
                permission(HttpMethod.GET, EMPLOYEES + "/{id}", Permission.WORKFORCE_MANAGE),
                permission(HttpMethod.POST, EMPLOYEES, Permission.WORKFORCE_MANAGE),
                permission(HttpMethod.PATCH, EMPLOYEES + "/{id}", Permission.WORKFORCE_MANAGE),
                permission(HttpMethod.POST, EMPLOYEES + "/{id}/deactivate", Permission.WORKFORCE_MANAGE),
                permission(HttpMethod.POST, EMPLOYEES + "/{id}/reactivate", Permission.WORKFORCE_MANAGE));
    }

    private static SecuredRouteRegistry.Route permission(
            HttpMethod method, String pattern, Permission permission) {
        return SecuredRouteRegistry.Route.permission(method, pattern, permission.name());
    }
}
