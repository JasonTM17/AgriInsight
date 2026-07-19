package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.application.ConfiguredIdentityProvider;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class OidcConfiguredIdentityProvider implements ConfiguredIdentityProvider {

    private final String issuer;

    public OidcConfiguredIdentityProvider(OidcIdentityProperties properties) {
        this.issuer = Objects.requireNonNull(properties, "properties are required").issuerUri();
    }

    @Override
    public String issuer() {
        return issuer;
    }
}
