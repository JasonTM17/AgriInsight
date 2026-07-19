package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;

final class AgriInsightAuthenticationToken extends AbstractAuthenticationToken {

    private final AgriInsightPrincipal principal;

    AgriInsightAuthenticationToken(AgriInsightPrincipal principal) {
        super(List.of());
        this.principal = Objects.requireNonNull(principal, "principal is required");
        setAuthenticated(true);
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
