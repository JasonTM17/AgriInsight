package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.application.IdentityBootstrap;
import com.agriinsight.backend.identity.application.IdentityBootstrapPort;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresIdentityBootstrapRepository implements IdentityBootstrapPort {

    private static final String RESOLVE_IDENTITY = """
            SELECT profile_id, tenant_id, profile_active, tenant_active
            FROM agriinsight_security.resolve_identity_bootstrap(?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresIdentityBootstrapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdentityBootstrap> findByIssuerAndSubject(String issuer, String subject) {
        List<IdentityBootstrap> matches = jdbcTemplate.query(
                RESOLVE_IDENTITY,
                (result, rowNumber) -> new IdentityBootstrap(
                        result.getObject("profile_id", java.util.UUID.class),
                        result.getObject("tenant_id", java.util.UUID.class),
                        result.getBoolean("profile_active"),
                        result.getBoolean("tenant_active")),
                issuer,
                subject);
        if (matches.size() > 1) {
            throw new IllegalStateException("Identity bootstrap resolver returned more than one row");
        }
        return matches.stream().findFirst();
    }
}
