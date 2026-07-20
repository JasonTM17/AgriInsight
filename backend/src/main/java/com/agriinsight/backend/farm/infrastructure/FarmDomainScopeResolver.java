package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.application.ScopeResolver;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FarmDomainScopeResolver implements ScopeResolver.DomainScopeResolver {

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
        if (principal == null || roles == null || permission == null || resourceId == null) {
            return ScopeResolver.DomainAccess.DENIED;
        }
        if (roles.stream().anyMatch(role -> role.tenantWide() && role.grants(permission))) {
            return ScopeResolver.DomainAccess.TENANT_WIDE;
        }
        if (roles.contains(Role.FARM_MANAGER) && Role.FARM_MANAGER.grants(permission)) {
            return ScopeResolver.DomainAccess.DOMAIN;
        }
        return ScopeResolver.DomainAccess.DENIED;
    }
}
