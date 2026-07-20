package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.shared.api.ApiVersion;
import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CurrentUserRoutes implements SecuredRouteRegistry.Contributor {

    @Override
    public Collection<SecuredRouteRegistry.Route> routes() {
        return List.of(SecuredRouteRegistry.Route.authenticated(HttpMethod.GET, ApiVersion.PREFIX + "/me"));
    }
}
