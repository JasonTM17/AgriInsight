package com.agriinsight.backend.identity.application;

import java.util.Optional;

public interface IdentityBootstrapPort {

    Optional<IdentityBootstrap> findByIssuerAndSubject(String issuer, String subject);
}
