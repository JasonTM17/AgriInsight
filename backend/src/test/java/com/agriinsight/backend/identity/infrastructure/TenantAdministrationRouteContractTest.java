package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.api.TenantRoleRoutes;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.identity.api.CurrentUserRoutes;
import com.agriinsight.backend.identity.api.TenantUserRoutes;
import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class TenantAdministrationRouteContractTest {

    @Test
    void registersOnlyTheExactTenantAdministrationTemplates() {
        SecuredRouteRegistry registry = new SecuredRouteRegistry(List.of(
                new CurrentUserRoutes(),
                new TenantUserRoutes(),
                new TenantRoleRoutes()));

        assertThat(registry.routes()).extracting(route -> route.method() + " " + route.pattern())
                .containsExactly(
                        "GET /api/v1/me",
                        "GET /api/v1/users",
                        "GET /api/v1/users/{id}",
                        "GET /api/v1/users/{id}/external-identities",
                        "GET /api/v1/users/{id}/roles",
                        "POST /api/v1/users",
                        "POST /api/v1/users/{id}/deactivate",
                        "POST /api/v1/users/{id}/external-identities",
                        "POST /api/v1/users/{id}/external-identities/{identityId}/unlink",
                        "POST /api/v1/users/{id}/reactivate",
                        "POST /api/v1/users/{id}/roles",
                        "POST /api/v1/users/{id}/roles/{roleCode}/revoke");
        assertThat(registry.routes())
                .filteredOn(route -> route.method() == HttpMethod.GET
                        && route.pattern().endsWith("/external-identities"))
                .extracting(SecuredRouteRegistry.Route::requiredAuthority)
                .containsOnly(Optional.of(Permission.IDENTITY_USER_MANAGE.name()));
        assertThat(registry.routes())
                .filteredOn(route -> route.method() == HttpMethod.GET
                        && route.pattern().endsWith("/roles"))
                .extracting(SecuredRouteRegistry.Route::requiredAuthority)
                .containsOnly(Optional.of(Permission.IDENTITY_ROLE_MANAGE.name()));
        assertThat(registry.routes())
                .filteredOn(route -> route.method() == HttpMethod.POST
                        && !route.pattern().contains("/roles"))
                .extracting(SecuredRouteRegistry.Route::requiredAuthority)
                .containsOnly(Optional.of(Permission.IDENTITY_USER_MANAGE.name()));
        assertThat(registry.routes())
                .filteredOn(route -> route.method() == HttpMethod.POST
                        && route.pattern().contains("/roles"))
                .extracting(SecuredRouteRegistry.Route::requiredAuthority)
                .containsOnly(Optional.of(Permission.IDENTITY_ROLE_MANAGE.name()));
    }
}
