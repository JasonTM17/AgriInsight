package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class OidcJwtCryptographyTest {

    @Test
    void acceptsTrustedRs256AndRejectsBadSignatureDifferentAlgorithmOrUnsignedJwt() throws Exception {
        RSAKey trustedKey = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
        RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID("untrusted-key").generate();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(trustedKey.toRSAPublicKey())
                .validateType(false)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new OidcJwtValidator(properties()));
        JWTClaimsSet claims = claims();

        assertThat(decoder.decode(sign(claims, trustedKey, JWSAlgorithm.RS256)).getSubject())
                .isEqualTo("Provider-Subject-001");
        assertThatThrownBy(() -> decoder.decode(sign(claims, untrustedKey, JWSAlgorithm.RS256)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(claims, trustedKey, JWSAlgorithm.RS512)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(new PlainJWT(claims).serialize()))
                .isInstanceOf(JwtException.class);
    }

    private String sign(JWTClaimsSet claims, RSAKey key, JWSAlgorithm algorithm) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private JWTClaimsSet claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("https://identity.example.test/issuer")
                .subject("Provider-Subject-001")
                .audience("agriinsight-api")
                .issueTime(Date.from(now.minusSeconds(30)))
                .notBeforeTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(90)))
                .claim("token_use", "access")
                .build();
    }

    private OidcIdentityProperties properties() {
        return new OidcIdentityProperties(
                true,
                "https://identity.example.test/issuer",
                "https://identity.example.test/jwks",
                "agriinsight-api",
                "interactive-client",
                Duration.ofSeconds(30),
                SignatureAlgorithm.RS256,
                OidcIdentityProperties.DiscriminatorLocation.CLAIM,
                "token_use",
                "access",
                "name",
                "email",
                "acr",
                List.of());
    }
}
