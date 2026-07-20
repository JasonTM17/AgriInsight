package com.agriinsight.backend.shared.api;

import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.IdempotencyKey;
import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import com.agriinsight.backend.shared.domain.CanonicalCommandMaterial;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ApiCommandFingerprintFactory {

    private final CanonicalCommandHasher hasher = new CanonicalCommandHasher();

    public CommandExecutionRequest create(
            TenantPrincipal principal,
            String rawIdempotencyKey,
            String httpMethod,
            String routeTemplate,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryValues,
            String canonicalBody,
            Map<String, String> semanticHeaders,
            String correlationId) {
        Objects.requireNonNull(principal, "principal is required");
        CanonicalCommandMaterial material = new CanonicalCommandMaterial(
                httpMethod,
                routeTemplate,
                pathVariables,
                queryValues,
                canonicalBody,
                semanticHeaders);
        return new CommandExecutionRequest(
                principal.tenantId(),
                principal.profileId(),
                IdempotencyKey.parse(rawIdempotencyKey),
                hasher.fingerprint(CanonicalCommandHasher.CURRENT_SCHEMA_VERSION, material),
                Optional.ofNullable(correlationId));
    }
}
