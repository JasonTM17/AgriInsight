package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantPrincipalLoader {

    private final IdentityBootstrapPort bootstrapPort;
    private final TenantPrincipalPort principalPort;
    private final TenantContextBinder contextBinder;
    private final TransactionTemplate transaction;

    public TenantPrincipalLoader(
            IdentityBootstrapPort bootstrapPort,
            TenantPrincipalPort principalPort,
            TenantContextBinder contextBinder,
            PlatformTransactionManager transactionManager) {
        this.bootstrapPort = Objects.requireNonNull(bootstrapPort, "bootstrapPort is required");
        this.principalPort = Objects.requireNonNull(principalPort, "principalPort is required");
        this.contextBinder = Objects.requireNonNull(contextBinder, "contextBinder is required");
        this.transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager is required"));
        this.transaction.setReadOnly(true);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AgriInsightPrincipal load(ExternalIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims are required");
        IdentityBootstrap bootstrap = bootstrapPort
                .findByIssuerAndSubject(claims.issuer(), claims.subject())
                .orElseThrow(() -> rejected(IdentityRejectionReason.UNKNOWN_IDENTITY));
        if (!bootstrap.profileActive()) {
            throw rejected(IdentityRejectionReason.PROFILE_DISABLED);
        }
        if (!bootstrap.tenantActive()) {
            throw rejected(IdentityRejectionReason.TENANT_DISABLED);
        }

        AgriInsightPrincipal principal = transaction.execute(status -> {
            contextBinder.bind(bootstrap.tenantId());
            TenantPrincipalData data = principalPort
                    .findActiveByProfileAndTenant(bootstrap.profileId(), bootstrap.tenantId())
                    .orElseThrow(() -> rejected(IdentityRejectionReason.PROFILE_DISABLED));
            return AgriInsightPrincipal.from(data, claims.assurance());
        });
        return Objects.requireNonNull(principal, "Tenant principal transaction returned no result");
    }

    private IdentityRejectedException rejected(IdentityRejectionReason reason) {
        return new IdentityRejectedException(reason);
    }
}
