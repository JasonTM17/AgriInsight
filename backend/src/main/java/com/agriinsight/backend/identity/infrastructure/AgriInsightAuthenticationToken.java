package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

final class AgriInsightAuthenticationToken extends AbstractAuthenticationToken {

    private final AgriInsightPrincipal principal;

    AgriInsightAuthenticationToken(AgriInsightPrincipal principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<SimpleGrantedAuthority> authorities(AgriInsightPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        return principal.permissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AgriInsightPrincipal getPrincipal() {
        return principal;
    }
}
