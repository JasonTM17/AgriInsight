package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.application.IdentityRejectedException;
import com.agriinsight.backend.identity.application.PrincipalMapper;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtPrincipalAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final OAuth2Error INVALID_IDENTITY = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token authentication failed",
            null);

    private final PrincipalMapper principalMapper;

    public JwtPrincipalAuthenticationConverter(PrincipalMapper principalMapper) {
        this.principalMapper = Objects.requireNonNull(principalMapper, "principalMapper is required");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            return new AgriInsightAuthenticationToken(principalMapper.map(jwt));
        } catch (IdentityRejectedException | IllegalArgumentException exception) {
            throw new OAuth2AuthenticationException(INVALID_IDENTITY, exception);
        }
    }
}
