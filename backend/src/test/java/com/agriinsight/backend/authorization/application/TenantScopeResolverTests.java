package com.agriinsight.backend.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantScopeResolverTests {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
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
    void tenantWideReadersResolveOnlyWithDatabaseAuthorities() {
        PermissionEvaluator evaluator = evaluator();
        bindTenant();

        for (Role role : List.of(Role.EXECUTIVE, Role.DATA_ANALYST, Role.TENANT_ADMIN)) {
            authenticate(Set.of(role), Permission.COST_READ);

            ScopeContext scope = evaluator.requireTenant(Permission.COST_READ);

            assertThat(scope.tenantId()).isEqualTo(TENANT_ID);
            assertThat(scope.profileId()).isEqualTo(PROFILE_ID);
            assertThat(scope.type()).isEqualTo(ScopeContext.Type.TENANT);
            assertThat(scope.resourceId()).isEmpty();
        }
    }

    @Test
    void supplierAndMissingPermissionDenyEvenWhenAuthenticated() {
        PermissionEvaluator evaluator = evaluator();
        bindTenant();

        authenticate(Set.of(Role.SUPPLIER), Permission.COST_READ);
        assertThatThrownBy(() -> evaluator.requireTenant(Permission.COST_READ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access is denied");

        authenticate(Set.of(Role.DATA_ANALYST));
        assertThatThrownBy(() -> evaluator.requireTenant(Permission.COST_READ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access is denied");
    }

    @Test
    void tenantResolvedPermissionDenialCarriesRedactedAuditMetadata() {
        PermissionEvaluator evaluator = evaluator();
        bindTenant();
        authenticate(Set.of(Role.SUPPLIER));

        assertThatThrownBy(() -> evaluator.requireTenant(Permission.COST_READ))
                .isInstanceOfSatisfying(TenantAuthorizationDeniedException.class, exception -> {
                    assertThat(exception.decision().tenantId()).isEqualTo(TENANT_ID);
                    assertThat(exception.decision().principalId()).isEqualTo(PROFILE_ID);
                    assertThat(exception.decision().targetReference())
                            .isEqualTo("permission=COST_READ;scope=TENANT");
                    assertThat(exception.decision().reasonCode()).isEqualTo("MISSING_PERMISSION");
                    assertThat(exception.decision().correlationId()).isEmpty();
                });
    }

    @Test
    void tenantScopeDoesNotCombineManagerPermissionWithReadOnlyRoleScope() {
        PermissionEvaluator evaluator = evaluator();
        bindTenant();
        authenticate(
                Set.of(Role.FARM_MANAGER, Role.DATA_ANALYST),
                Permission.FARM_MANAGE,
                Permission.FARM_READ);

        assertThatThrownBy(() -> evaluator.requireTenant(Permission.FARM_MANAGE))
                .isInstanceOfSatisfying(TenantAuthorizationDeniedException.class, exception ->
                        assertThat(exception.decision().reasonCode()).isEqualTo("SCOPE_UNRESOLVED"));
    }

    private PermissionEvaluator evaluator() {
        return new PermissionEvaluator(new ScopeResolver(List.of()), contextState);
    }

    private void bindTenant() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        contextState.bind(TENANT_ID);
    }

    private void authenticate(Set<Role> roles, Permission... permissions) {
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
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
