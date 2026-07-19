package com.agriinsight.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import com.agriinsight.backend.identity.infrastructure.SecuredRouteRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@IdentitySecurityContext
class IdentityEndpointInventoryTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private SecuredRouteRegistry routeRegistry;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityBootstrapPort bootstrapPort;

    @Test
    void everyApplicationControllerMappingHasAnExactRegistryEntry() {
        List<String> unregistered = new ArrayList<>();
        TreeSet<String> controllerRoutes = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.agriinsight.backend")) {
                return;
            }
            if (mapping.getMethodsCondition().getMethods().isEmpty()) {
                mapping.getPatternValues().forEach(pattern -> unregistered.add("ANY " + pattern));
                return;
            }
            for (RequestMethod requestMethod : mapping.getMethodsCondition().getMethods()) {
                HttpMethod method = HttpMethod.valueOf(requestMethod.name());
                mapping.getPatternValues().forEach(pattern -> {
                    String route = method + " " + pattern;
                    controllerRoutes.add(route);
                    if (!routeRegistry.contains(method, pattern)) {
                        unregistered.add(route);
                    }
                });
            }
        });

        assertThat(unregistered).isEmpty();
        assertThat(routeRegistry.routes()).extracting(route -> route.method() + " " + route.pattern())
                .containsExactlyElementsOf(controllerRoutes);
    }
}
