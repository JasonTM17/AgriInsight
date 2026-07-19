package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ScopeResolver {

    private static final Set<Role> TENANT_WIDE_ROLES = Set.of(
            Role.TENANT_ADMIN,
            Role.EXECUTIVE,
            Role.DATA_ANALYST);

    private final Map<ScopeContext.Type, DomainScopeResolver> domainResolvers;

    public ScopeResolver(List<DomainScopeResolver> resolvers) {
        Objects.requireNonNull(resolvers, "resolvers are required");
        Map<ScopeContext.Type, DomainScopeResolver> indexed =
                new EnumMap<>(ScopeContext.Type.class);
        for (DomainScopeResolver resolver : resolvers) {
            Objects.requireNonNull(resolver, "resolver is required");
            ScopeContext.Type resolverType =
                    Objects.requireNonNull(resolver.type(), "resolver type is required");
            if (resolverType == ScopeContext.Type.TENANT) {
                throw new IllegalArgumentException("Tenant scope is resolved by the authorization core");
            }
            if (indexed.putIfAbsent(resolverType, resolver) != null) {
                throw new IllegalArgumentException("Duplicate scope resolver: " + resolverType);
            }
        }
        this.domainResolvers = Map.copyOf(indexed);
    }

    public Optional<ScopeContext> resolve(
            Authentication authentication,
            TenantPrincipal principal,
            ScopeContext.Type type,
            Optional<UUID> resourceId) {
        Objects.requireNonNull(authentication, "authentication is required");
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
        if (!(authentication.getPrincipal() instanceof TenantPrincipal authenticatedPrincipal)
                || !authenticatedPrincipal.profileId().equals(principal.profileId())
                || !authenticatedPrincipal.tenantId().equals(principal.tenantId())) {
            return Optional.empty();
        }

        Set<Role> roles = roles(authentication.getAuthorities());
        if (type == ScopeContext.Type.TENANT) {
            if (resourceId.isPresent() || roles.stream().noneMatch(TENANT_WIDE_ROLES::contains)) {
                return Optional.empty();
            }
            return Optional.of(ScopeContext.tenant(principal));
        }

        DomainScopeResolver resolver = domainResolvers.get(type);
        if (resolver == null || !resolver.permits(principal, roles, resourceId)) {
            return Optional.empty();
        }
        return Optional.of(ScopeContext.domain(principal, type, resourceId));
    }

    private Set<Role> roles(Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        for (Role role : Role.values()) {
            if (authorities.stream().anyMatch(authority -> role.authority().equals(authority.getAuthority()))) {
                roles.add(role);
            }
        }
        return Set.copyOf(roles);
    }

    public interface DomainScopeResolver {

        ScopeContext.Type type();

        boolean permits(
                TenantPrincipal principal,
                Set<Role> roles,
                Optional<UUID> resourceId);
    }
}
