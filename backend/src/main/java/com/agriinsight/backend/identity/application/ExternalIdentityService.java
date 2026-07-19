package com.agriinsight.backend.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ExternalIdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalIdentityService.class);

    private final IdentityBootstrapPort bootstrapPort;

    public ExternalIdentityService(IdentityBootstrapPort bootstrapPort) {
        this.bootstrapPort = bootstrapPort;
    }

    @Transactional(readOnly = true)
    public AgriInsightPrincipal resolve(ExternalIdentityClaims claims) {
        IdentityBootstrap bootstrap = bootstrapPort
                .findByIssuerAndSubject(claims.issuer(), claims.subject())
                .orElseThrow(() -> rejected(IdentityRejectionReason.UNKNOWN_IDENTITY, claims));
        if (!bootstrap.profileActive()) {
            throw rejected(IdentityRejectionReason.PROFILE_DISABLED, claims);
        }
        if (!bootstrap.tenantActive()) {
            throw rejected(IdentityRejectionReason.TENANT_DISABLED, claims);
        }
        return new AgriInsightPrincipal(
                bootstrap.profileId(),
                bootstrap.tenantId(),
                Optional.ofNullable(claims.displayName()),
                Optional.ofNullable(claims.email()),
                Optional.ofNullable(claims.assurance()));
    }

    private IdentityRejectedException rejected(
            IdentityRejectionReason reason,
            ExternalIdentityClaims claims) {
        LOGGER.warn(
                "security.identity_rejected reason={} subjectFingerprint={}",
                reason,
                fingerprint(claims.issuer(), claims.subject()));
        return new IdentityRejectedException(reason);
    }

    private String fingerprint(String issuer, String subject) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = (issuer.length() + ":" + issuer + subject).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(value), 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
