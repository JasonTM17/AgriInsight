package com.agriinsight.backend.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import com.agriinsight.backend.shared.persistence.TenantContextRequiredException;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DomainScopeResolverTests {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal(PROFILE_ID, TENANT_ID);
    private final TenantContextState contextState = new TenantContextState();

    @AfterEach
    void clearThreadState() {
        SecurityContextHolder.clearContext();
        if (contextState.currentTenantId().isPresent()) {
            contextState.unbind();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void uninstalledDomainScopeDeniesUntilItsModuleProvidesAResolver() {
        PermissionEvaluator evaluator = evaluator(List.of());
        bindTenant();
        authenticate(Role.TENANT_ADMIN, Permission.FARM_READ);

        assertThatThrownBy(() -> evaluator.requireDomain(
                        Permission.FARM_READ,
                        ScopeContext.Type.FARM,
                        FARM_ID))
                .isInstanceOfSatisfying(TenantAuthorizationDeniedException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("Access is denied");
                    assertThat(exception.decision().reasonCode()).isEqualTo("SCOPE_UNRESOLVED");
                    assertThat(exception.decision().targetId()).contains(FARM_ID);
                });
    }

    @Test
    void installedDomainResolverProducesRepositoryScopeWithoutChangingTenant() {
        ScopeResolver.DomainScopeResolver farmResolver = new ScopeResolver.DomainScopeResolver() {
            @Override
            public ScopeContext.Type type() {
                return ScopeContext.Type.FARM;
            }

            @Override
            public ScopeResolver.DomainAccess access(
                    TenantPrincipal principal,
                    Set<Role> roles,
                    Permission permission,
                    Optional<UUID> resourceId) {
                boolean permitted = principal.tenantId().equals(TENANT_ID)
                        && roles.contains(Role.FARM_MANAGER)
                        && Role.FARM_MANAGER.grants(permission)
                        && resourceId.equals(Optional.of(FARM_ID));
                return permitted
                        ? ScopeResolver.DomainAccess.DOMAIN
                        : ScopeResolver.DomainAccess.DENIED;
            }
        };
        PermissionEvaluator evaluator = evaluator(List.of(farmResolver));
        bindTenant();
        authenticate(Role.FARM_MANAGER, Permission.FARM_READ);

        ScopeContext scope = evaluator.requireDomain(
                Permission.FARM_READ,
                ScopeContext.Type.FARM,
                FARM_ID);

        assertThat(scope.tenantId()).isEqualTo(TENANT_ID);
        assertThat(scope.profileId()).isEqualTo(PROFILE_ID);
        assertThat(scope.type()).isEqualTo(ScopeContext.Type.FARM);
        assertThat(scope.resourceId()).contains(FARM_ID);
    }

    @Test
    void duplicateResolversAndPermissionChecksOutsideTenantTransactionsFailClosed() {
        ScopeResolver.DomainScopeResolver farmResolver = resolver(ScopeContext.Type.FARM);
        assertThatThrownBy(() -> new ScopeResolver(List.of(farmResolver, farmResolver)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate scope resolver: FARM");

        authenticate(Role.TENANT_ADMIN, Permission.FARM_READ);
        assertThatThrownBy(() -> evaluator(List.of()).requireTenant(Permission.FARM_READ))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Matching tenant context is required");
    }

    private PermissionEvaluator evaluator(List<ScopeResolver.DomainScopeResolver> resolvers) {
        return new PermissionEvaluator(new ScopeResolver(resolvers), contextState);
    }

    private void bindTenant() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        contextState.bind(TENANT_ID);
    }

    private ScopeResolver.DomainScopeResolver resolver(ScopeContext.Type type) {
        return new ScopeResolver.DomainScopeResolver() {
            @Override
            public ScopeContext.Type type() {
                return type;
            }

            @Override
            public ScopeResolver.DomainAccess access(
                    TenantPrincipal principal,
                    Set<Role> roles,
                    Permission permission,
                    Optional<UUID> resourceId) {
                return ScopeResolver.DomainAccess.DOMAIN;
            }
        };
    }

    private void authenticate(Role role, Permission... permissions) {
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority(role.authority()));
        for (Permission permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission.authority()));
        }
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null, authorities));
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }
}
