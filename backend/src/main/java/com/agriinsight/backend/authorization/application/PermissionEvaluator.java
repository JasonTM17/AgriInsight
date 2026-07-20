package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PermissionEvaluator {

    private static final String ACCESS_DENIED = "Access is denied";

    private final ScopeResolver scopeResolver;
    private final TenantContextState contextState;

    public PermissionEvaluator(
            ScopeResolver scopeResolver,
            TenantContextState contextState) {
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver is required");
        this.contextState = Objects.requireNonNull(contextState, "contextState is required");
    }

    public ScopeContext requireTenant(Permission permission) {
        return require(permission, ScopeContext.Type.TENANT, Optional.empty());
    }

    public ScopeContext requireDomain(
            Permission permission,
            ScopeContext.Type type,
            UUID resourceId) {
        return require(permission, type, Optional.of(Objects.requireNonNull(resourceId, "resourceId is required")));
    }

    public ScopeContext requireDomainList(Permission permission, ScopeContext.Type type) {
        return require(permission, type, Optional.empty());
    }

    private ScopeContext require(
            Permission permission,
            ScopeContext.Type type,
            Optional<UUID> resourceId) {
        Objects.requireNonNull(permission, "permission is required");
        Objects.requireNonNull(type, "type is required");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new AccessDeniedException(ACCESS_DENIED);
        }
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> permission.authority().equals(authority.getAuthority()))) {
            throw denied(principal, permission, type, resourceId, "MISSING_PERMISSION");
        }
        contextState.requireBound(principal.tenantId());

        Optional<ScopeContext> resolved = scopeResolver.resolve(
                authentication, principal, permission, type, resourceId);
        if (resolved.isEmpty()) {
            throw denied(principal, permission, type, resourceId, "SCOPE_UNRESOLVED");
        }
        return resolved.orElseThrow();
    }

    private TenantAuthorizationDeniedException denied(
            TenantPrincipal principal,
            Permission permission,
            ScopeContext.Type type,
            Optional<UUID> resourceId,
            String reasonCode) {
        return new TenantAuthorizationDeniedException(new TenantAuthorizationDeniedRecorder.Decision(
                principal.tenantId(),
                principal.profileId(),
                "permission=" + permission.name() + ";scope=" + type.name(),
                reasonCode,
                resourceId,
                Optional.empty()));
    }
}
