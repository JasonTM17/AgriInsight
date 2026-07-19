package com.agriinsight.backend.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ExternalIdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalIdentityService.class);

    private final TenantPrincipalLoader principalLoader;

    public ExternalIdentityService(TenantPrincipalLoader principalLoader) {
        this.principalLoader = principalLoader;
    }

    public AgriInsightPrincipal resolve(ExternalIdentityClaims claims) {
        try {
            return principalLoader.load(claims);
        } catch (IdentityRejectedException exception) {
            logRejection(exception.reason(), claims);
            throw exception;
        }
    }

    private void logRejection(
            IdentityRejectionReason reason,
            ExternalIdentityClaims claims) {
        LOGGER.warn(
                "security.identity_rejected reason={} subjectFingerprint={}",
                reason,
                fingerprint(claims.issuer(), claims.subject()));
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
