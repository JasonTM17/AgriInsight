package com.agriinsight.backend.operations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.ScopeResolver;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityDomainScopeResolverTest {

    private static final TenantPrincipal PRINCIPAL = new TestPrincipal(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000001"));

    private final ActivityDomainScopeResolver resolver = new ActivityDomainScopeResolver();

    @Test
    void resolvesTenantManagerAndWorkerAccessWithoutWideningRoles() {
        assertThat(resolver.type()).isEqualTo(ScopeContext.Type.ACTIVITY);
        assertThat(access(Set.of(Role.TENANT_ADMIN), Permission.ACTIVITY_MANAGE))
                .isEqualTo(ScopeResolver.DomainAccess.TENANT_WIDE);
        assertThat(access(Set.of(Role.DATA_ANALYST), Permission.ACTIVITY_READ))
                .isEqualTo(ScopeResolver.DomainAccess.TENANT_WIDE);
        assertThat(access(Set.of(Role.FARM_MANAGER), Permission.ACTIVITY_MANAGE))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
        assertThat(access(Set.of(Role.FIELD_WORKER), Permission.ACTIVITY_READ))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
        assertThat(access(Set.of(Role.FIELD_WORKER), Permission.ACTIVITY_LOG_APPEND))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
        assertThat(access(Set.of(Role.FIELD_WORKER), Permission.ACTIVITY_MANAGE))
                .isEqualTo(ScopeResolver.DomainAccess.DENIED);
        assertThat(access(Set.of(Role.SUPPLIER), Permission.ACTIVITY_READ))
                .isEqualTo(ScopeResolver.DomainAccess.DENIED);
    }

    @Test
    void tenantWideGrantWinsForMixedReadRolesButNotForManagement() {
        Set<Role> mixed = Set.of(Role.DATA_ANALYST, Role.FARM_MANAGER);

        assertThat(access(mixed, Permission.ACTIVITY_READ))
                .isEqualTo(ScopeResolver.DomainAccess.TENANT_WIDE);
        assertThat(access(mixed, Permission.ACTIVITY_MANAGE))
                .isEqualTo(ScopeResolver.DomainAccess.DOMAIN);
    }

    private ScopeResolver.DomainAccess access(Set<Role> roles, Permission permission) {
        return resolver.access(PRINCIPAL, roles, permission, Optional.empty());
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }
}
