package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Permission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class SecuredRouteRegistryTest {

    @Test
    void keepsExactMethodPatternAndMinimumPermissionContracts() {
        SecuredRouteRegistry registry = new SecuredRouteRegistry(List.of(() -> List.of(
                SecuredRouteRegistry.Route.authenticated(HttpMethod.GET, "/api/v1/me"),
                SecuredRouteRegistry.Route.permission(
                        HttpMethod.GET,
                        "/api/v1/farms/{id}",
                        Permission.FARM_READ))));

        assertThat(registry.contains(HttpMethod.GET, "/api/v1/me")).isTrue();
        assertThat(registry.contains(HttpMethod.POST, "/api/v1/me")).isFalse();
        assertThat(registry.contains(HttpMethod.GET, "/api/v1/farms/**")).isFalse();
        assertThat(registry.routes()).hasSize(2);
    }

    @Test
    void rejectsDuplicateOrWildcardBusinessRoutes() {
        assertThatThrownBy(() -> new SecuredRouteRegistry(List.of(
                () -> List.of(SecuredRouteRegistry.Route.authenticated(HttpMethod.GET, "/api/v1/me")),
                () -> List.of(SecuredRouteRegistry.Route.authenticated(HttpMethod.GET, "/api/v1/me")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> SecuredRouteRegistry.Route.authenticated(
                HttpMethod.GET,
                "/api/v1/farms/**"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }
}
