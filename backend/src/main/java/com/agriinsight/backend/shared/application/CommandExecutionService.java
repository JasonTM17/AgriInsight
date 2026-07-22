package com.agriinsight.backend.shared.application;

import com.agriinsight.backend.shared.domain.ApiCommandRecord;
import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import com.agriinsight.backend.shared.persistence.TenantContextRequiredException;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class CommandExecutionService {

    private final ApiCommandRecordStore store;
    private final TenantContextState contextState;
    private final IdempotencyConflictPublisher conflictPublisher;
    private final ApplicationEventPublisher eventPublisher;

    public CommandExecutionService(
            ApiCommandRecordStore store,
            TenantContextState contextState,
            IdempotencyConflictPublisher conflictPublisher) {
        this(store, contextState, conflictPublisher, event -> { });
    }

    @Autowired
    public CommandExecutionService(
            ApiCommandRecordStore store,
            TenantContextState contextState,
            IdempotencyConflictPublisher conflictPublisher,
            ApplicationEventPublisher eventPublisher) {
        this.store = Objects.requireNonNull(store, "store is required");
        this.contextState = Objects.requireNonNull(contextState, "contextState is required");
        this.conflictPublisher = Objects.requireNonNull(conflictPublisher, "conflictPublisher is required");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher is required");
    }

    public <T> CommandExecutionResult<T> execute(
            CommandExecutionRequest request,
            Supplier<CommandCompletion<T>> mutation,
            Function<CommandTarget, Optional<T>> replayLoader) {
        Objects.requireNonNull(mutation, "mutation is required");
        return executeInternal(request, ignored -> mutation.get(), replayLoader);
    }

    public <T> CommandExecutionResult<T> executeWithCommandId(
            CommandExecutionRequest request,
            Function<UUID, CommandCompletion<T>> mutation,
            Function<CommandTarget, Optional<T>> replayLoader) {
        Objects.requireNonNull(mutation, "mutation is required");
        return executeInternal(
                request, record -> mutation.apply(record.commandId()), replayLoader);
    }

    private <T> CommandExecutionResult<T> executeInternal(
            CommandExecutionRequest request,
            Function<ApiCommandRecord, CommandCompletion<T>> mutation,
            Function<CommandTarget, Optional<T>> replayLoader) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(mutation, "mutation is required");
        Objects.requireNonNull(replayLoader, "replayLoader is required");
        requireAuthenticatedActor(request);
        contextState.requireBound(request.tenantId());

        ApiCommandRecord reservation = reservation(request);
        ApiCommandRecordStore.Claim claim = store.claim(reservation);
        requireBinding(claim.record(), request);
        if (claim.claimed()) {
            return applyMutation(
                    claim.record(), request, () -> mutation.apply(claim.record()));
        }
        if (!claim.record().matches(
                request.fingerprint().schemaVersion(),
                request.fingerprint().commandHash())) {
            return conflict(claim.record(), request);
        }
        return replay(claim.record(), replayLoader);
    }

    private ApiCommandRecord reservation(CommandExecutionRequest request) {
        CanonicalCommandHasher.Fingerprint fingerprint = request.fingerprint();
        return ApiCommandRecord.inProgress(
                UUID.randomUUID(),
                request.tenantId(),
                request.principalId(),
                fingerprint.httpMethod(),
                fingerprint.routeTemplate(),
                request.idempotencyKey().digest(),
                fingerprint.schemaVersion(),
                fingerprint.commandHash());
    }

    private <T> CommandExecutionResult<T> applyMutation(
            ApiCommandRecord reservation,
            CommandExecutionRequest request,
            Supplier<CommandCompletion<T>> mutation) {
        CommandCompletion<T> completion = Objects.requireNonNull(
                mutation.get(),
                "mutation completion is required");
        ApiCommandRecord completed = reservation.complete(
                completion.responseStatus(),
                completion.target().resourceType(),
                completion.target().resourceId(),
                completion.target().resourceVersion());
        ApiCommandRecord persisted = store.complete(completed);
        if (!persisted.equals(completed)) {
            throw new IllegalStateException("Command completion store returned unexpected metadata");
        }
        eventPublisher.publishEvent(new CommandCommittedEvent(
                reservation.tenantId(),
                reservation.principalId(),
                reservation.commandId(),
                request.fingerprint().routeTemplate(),
                completion.target(),
                request.correlationId(),
                java.time.Instant.now(),
                0));
        return new CommandExecutionResult.Completed<>(
                persisted.commandId(),
                false,
                completion.responseStatus(),
                completion.target(),
                completion.representation());
    }

    private <T> CommandExecutionResult<T> conflict(
            ApiCommandRecord existing,
            CommandExecutionRequest request) {
        conflictPublisher.publish(new IdempotencyConflictPublisher.Conflict(
                request.tenantId(),
                request.principalId(),
                existing.commandId(),
                request.fingerprint().routeTemplate(),
                request.correlationId()));
        return new CommandExecutionResult.Conflict<>(existing.commandId());
    }

    private <T> CommandExecutionResult<T> replay(
            ApiCommandRecord existing,
            Function<CommandTarget, Optional<T>> replayLoader) {
        if (existing.state() != ApiCommandRecord.State.COMPLETED) {
            throw new IllegalStateException("An externally visible command must not remain in-progress");
        }
        CommandTarget target = new CommandTarget(
                existing.targetResourceType().orElseThrow(),
                existing.targetResourceId().orElseThrow(),
                existing.targetVersion().orElseThrow());
        Optional<T> representation = Objects.requireNonNull(
                replayLoader.apply(target),
                "replay representation is required");
        return new CommandExecutionResult.Completed<>(
                existing.commandId(),
                true,
                existing.responseStatus().orElseThrow(),
                target,
                representation);
    }

    private void requireBinding(ApiCommandRecord record, CommandExecutionRequest request) {
        CanonicalCommandHasher.Fingerprint fingerprint = request.fingerprint();
        if (!record.tenantId().equals(request.tenantId())
                || !record.principalId().equals(request.principalId())
                || !record.httpMethod().equals(fingerprint.httpMethod())
                || !record.routeTemplate().equals(fingerprint.routeTemplate())
                || !record.idempotencyKeyDigest().equals(request.idempotencyKey().digest())) {
            throw new IllegalStateException("Command store returned a record from another external binding");
        }
    }

    private void requireAuthenticatedActor(CommandExecutionRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantPrincipal principal)
                || !principal.tenantId().equals(request.tenantId())
                || !principal.profileId().equals(request.principalId())) {
            throw new TenantContextRequiredException("Command actor must match the authenticated tenant principal");
        }
    }
}
