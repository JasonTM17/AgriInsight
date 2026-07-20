package com.agriinsight.backend.persistence.support;

import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAuditPublisher;
import com.agriinsight.backend.authorization.infrastructure.TenantIdempotencyConflictAuditPublisher;
import com.agriinsight.backend.shared.application.ApiCommandRecordStore;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import com.agriinsight.backend.shared.application.CommandTarget;
import com.agriinsight.backend.shared.application.IdempotencyKey;
import com.agriinsight.backend.shared.domain.ApiCommandRecord;
import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import com.agriinsight.backend.shared.domain.CanonicalCommandMaterial;
import com.agriinsight.backend.shared.persistence.PostgresApiCommandRecordStore;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class ApiCommandIntegrationFixture implements AutoCloseable {

    public static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID PRINCIPAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    public static final UUID TARGET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    public static final String ROUTE = "/api/v1/users/{id}";

    private final TenantTransactionTestHarness harness;
    private final CommandExecutionService service;

    private ApiCommandIntegrationFixture(
            PostgreSQLContainer container,
            Runnable beforeClaim) {
        harness = TenantTransactionTestHarness.runtime(container, "agriinsight");
        ApiCommandRecordStore postgresStore = new PostgresApiCommandRecordStore(harness.jdbcTemplate());
        ApiCommandRecordStore store = new SignalingStore(postgresStore, beforeClaim);
        var audit = new PostgresTenantAuditPublisher(harness.jdbcTemplate());
        service = new CommandExecutionService(
                store,
                harness.contextState(),
                new TenantIdempotencyConflictAuditPublisher(audit));
    }

    public static ApiCommandIntegrationFixture open(PostgreSQLContainer container) {
        return new ApiCommandIntegrationFixture(container, () -> {});
    }

    public static ApiCommandIntegrationFixture open(
            PostgreSQLContainer container,
            Runnable beforeClaim) {
        return new ApiCommandIntegrationFixture(container, beforeClaim);
    }

    public CommandExecutionRequest request(
            String rawKey,
            String pathId,
            String ifMatch,
            String canonicalBody) {
        CanonicalCommandMaterial material = new CanonicalCommandMaterial(
                "PATCH",
                ROUTE,
                Map.of("id", pathId),
                Map.of(),
                canonicalBody,
                Map.of("If-Match", ifMatch));
        return new CommandExecutionRequest(
                TENANT_ID,
                PRINCIPAL_ID,
                IdempotencyKey.parse(rawKey),
                new CanonicalCommandHasher().fingerprint((short) 1, material),
                Optional.of("integration-command-1"));
    }

    public <T> CommandExecutionResult<T> execute(
            CommandExecutionRequest request,
            Supplier<CommandCompletion<T>> mutation,
            Function<CommandTarget, Optional<T>> replayLoader) {
        TenantPrincipal principal = new TestPrincipal(PRINCIPAL_ID, TENANT_ID);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        try {
            return harness.withinTenant(() -> service.execute(request, mutation, replayLoader));
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Tenant command fixture failed", exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public void close() {
        harness.close();
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }

    private record SignalingStore(
            ApiCommandRecordStore delegate,
            Runnable beforeClaim) implements ApiCommandRecordStore {

        private SignalingStore {
            java.util.Objects.requireNonNull(delegate, "delegate is required");
            java.util.Objects.requireNonNull(beforeClaim, "beforeClaim is required");
        }

        @Override
        public Claim claim(ApiCommandRecord reservation) {
            beforeClaim.run();
            return delegate.claim(reservation);
        }

        @Override
        public ApiCommandRecord complete(ApiCommandRecord completedRecord) {
            return delegate.complete(completedRecord);
        }
    }
}
