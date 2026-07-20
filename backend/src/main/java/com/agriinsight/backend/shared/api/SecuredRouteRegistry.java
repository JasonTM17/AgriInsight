package com.agriinsight.backend.shared.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SecuredRouteRegistry {

    private final List<Route> routes;

    public SecuredRouteRegistry(List<Contributor> contributors) {
        List<Route> collected = new ArrayList<>();
        contributors.forEach(contributor -> collected.addAll(contributor.routes()));
        Set<String> keys = new HashSet<>();
        collected.forEach(route -> {
            if (!keys.add(key(route))) {
                throw new IllegalArgumentException("Duplicate secured route: " + key(route));
            }
        });
        this.routes = collected.stream().sorted((left, right) -> key(left).compareTo(key(right))).toList();
    }

    public List<Route> routes() {
        return routes;
    }

    public boolean contains(HttpMethod method, String pattern) {
        return routes.stream().anyMatch(route -> route.method().equals(method) && route.pattern().equals(pattern));
    }

    private static String key(Route route) {
        return route.method().name() + " " + route.pattern();
    }

    @FunctionalInterface
    public interface Contributor {
        Collection<Route> routes();
    }

    public record Route(HttpMethod method, String pattern, Optional<String> requiredAuthority) {

        public Route {
            Objects.requireNonNull(method, "method is required");
            Objects.requireNonNull(pattern, "pattern is required");
            requiredAuthority = Objects.requireNonNull(requiredAuthority, "requiredAuthority is required");
            requiredAuthority.ifPresent(authority -> {
                if (!authority.matches("[A-Z][A-Z0-9_]{0,127}")) {
                    throw new IllegalArgumentException("requiredAuthority has an invalid format");
                }
            });
            if (!pattern.startsWith("/api/v1/") || pattern.contains("*")) {
                throw new IllegalArgumentException("Business route must be an exact /api/v1 PathPattern without wildcard");
            }
            PathPatternParser.defaultInstance.parse(pattern);
        }

        public static Route authenticated(HttpMethod method, String pattern) {
            return new Route(method, pattern, Optional.empty());
        }

        public static Route permission(HttpMethod method, String pattern, String authority) {
            return new Route(method, pattern, Optional.of(Objects.requireNonNull(authority, "authority is required")));
        }

        public RequestMatcher requestMatcher() {
            return PathPatternRequestMatcher.pathPattern(method, pattern);
        }
    }
}
