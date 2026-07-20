package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.identity.application.ExternalIdentityReference;
import com.agriinsight.backend.identity.application.TenantExternalIdentityStore;
import com.agriinsight.backend.identity.domain.ExternalIdentity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresTenantExternalIdentityStore implements TenantExternalIdentityStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresTenantExternalIdentityStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public Optional<ExternalIdentityReference> findById(
            ScopeContext scope,
            UUID profileId,
            UUID identityId) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(identityId, "identityId is required");
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                SELECT identity_id, identity_issuer, identity_active, identity_version
                  FROM agriinsight_security.read_external_identity(?, ?, ?)
                """,
                (result, rowNumber) -> new ExternalIdentityReference(
                        result.getObject("identity_id", UUID.class),
                        result.getString("identity_issuer"),
                        result.getBoolean("identity_active"),
                        result.getLong("identity_version")),
                scope.tenantId(),
                profileId,
                identityId));
    }

    @Override
    public Optional<ExternalIdentityReference> link(
            ScopeContext scope,
            ExternalIdentity identity) {
        requireTenantScope(scope);
        Objects.requireNonNull(identity, "identity is required");
        requireSameTenant(scope, identity.getTenantId());
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                SELECT identity_id, identity_version
                  FROM agriinsight_security.link_external_identity_versioned(?, ?, ?, ?)
                """,
                (result, rowNumber) -> new ExternalIdentityReference(
                        result.getObject("identity_id", UUID.class),
                        identity.getIssuer(),
                        true,
                        result.getLong("identity_version")),
                identity.getId(),
                identity.getUserProfileId(),
                identity.getIssuer(),
                identity.getSubject()));
    }

    @Override
    public Optional<Long> unlink(
            ScopeContext scope,
            UUID profileId,
            UUID identityId) {
        requireTenantScope(scope);
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(identityId, "identityId is required");
        Long version = jdbcTemplate.queryForObject("""
                SELECT agriinsight_security.unlink_external_identity_versioned(?, ?)
                """, Long.class, profileId, identityId);
        return Optional.ofNullable(version);
    }

    private void requireTenantScope(ScopeContext scope) {
        Objects.requireNonNull(scope, "scope is required");
        if (scope.type() != ScopeContext.Type.TENANT || scope.resourceId().isPresent()) {
            throw new IllegalArgumentException("External identity store requires tenant-wide scope");
        }
    }

    private void requireSameTenant(ScopeContext scope, UUID tenantId) {
        if (!scope.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant-scoped data cannot switch tenants");
        }
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("External identity query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
