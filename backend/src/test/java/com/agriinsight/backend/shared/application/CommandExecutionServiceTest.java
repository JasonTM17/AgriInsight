package com.agriinsight.backend.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.shared.domain.ApiCommandRecord;
import com.agriinsight.backend.shared.domain.CanonicalCommandHasher;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CommandExecutionServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PRINCIPAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RESOURCE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final CanonicalCommandHasher.Fingerprint FINGERPRINT =
            new CanonicalCommandHasher.Fingerprint(
                    (short) 1,
                    "PATCH",
                    "/api/v1/users/{id}",
                    "b".repeat(64));

    private ApiCommandRecordStore store;
    private TenantContextState contextState;
    private IdempotencyConflictPublisher conflictPublisher;
    private ApplicationEventPublisher eventPublisher;
    private CommandExecutionService service;

    @BeforeEach
    void setUp() {
        store = mock(ApiCommandRecordStore.class);
        contextState = mock(TenantContextState.class);
        conflictPublisher = mock(IdempotencyConflictPublisher.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new CommandExecutionService(store, contextState, conflictPublisher, eventPublisher);
        TenantPrincipal principal = mock(TenantPrincipal.class);
        when(principal.tenantId()).thenReturn(TENANT_ID);
        when(principal.profileId()).thenReturn(PRINCIPAL_ID);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void claimedCommandRunsMutationAndPersistsReplayMetadata() {
        when(store.claim(any())).thenAnswer(invocation ->
                ApiCommandRecordStore.Claim.claimed(invocation.getArgument(0)));
        when(store.complete(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicInteger mutations = new AtomicInteger();

        CommandExecutionResult<UserView> result = service.execute(
                request(FINGERPRINT),
                () -> {
                    mutations.incrementAndGet();
                    return CommandCompletion.withRepresentation(
                            200,
                            "USER_PROFILE",
                            RESOURCE_ID,
                            7,
                            new UserView("Current"));
                },
                target -> Optional.of(new UserView("Replay")));

        assertThat(result).isInstanceOf(CommandExecutionResult.Completed.class);
        CommandExecutionResult.Completed<UserView> completed =
                (CommandExecutionResult.Completed<UserView>) result;
        assertThat(completed.commandId()).isNotNull();
        assertThat(completed.replayed()).isFalse();
        assertThat(completed.responseStatus()).isEqualTo(200);
        assertThat(completed.target().resourceId()).isEqualTo(RESOURCE_ID);
        assertThat(completed.representation()).contains(new UserView("Current"));
        assertThat(mutations).hasValue(1);
        verify(contextState).requireBound(TENANT_ID);
        verify(store).complete(any(ApiCommandRecord.class));
        verify(conflictPublisher, never()).publish(any());
        ArgumentCaptor<CommandCommittedEvent> event = ArgumentCaptor.forClass(CommandCommittedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().commandId()).isEqualTo(completed.commandId());
        assertThat(event.getValue().target()).isEqualTo(completed.target());
        assertThat(event.getValue().eventOrdinal()).isZero();
    }

    @Test
    void claimedCommandCanBindAnAppendOnlyLedgerToItsInternalCommandId() {
        when(store.claim(any())).thenAnswer(invocation ->
                ApiCommandRecordStore.Claim.claimed(invocation.getArgument(0)));
        when(store.complete(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicReference<UUID> referencedCommand = new AtomicReference<>();

        CommandExecutionResult<UserView> result = service.executeWithCommandId(
                request(FINGERPRINT),
                commandId -> {
                    referencedCommand.set(commandId);
                    return CommandCompletion.withRepresentation(
                            201, "USER_PROFILE", RESOURCE_ID, 0, new UserView("Created"));
                },
                target -> Optional.of(new UserView("Replay")));

        CommandExecutionResult.Completed<UserView> completed =
                (CommandExecutionResult.Completed<UserView>) result;
        assertThat(referencedCommand.get()).isEqualTo(completed.commandId());
    }

    @Test
    void completedSameCommandReconstructsCurrentlyAuthorizedRepresentation() {
        ApiCommandRecord completed = completedRecord(FINGERPRINT);
        when(store.claim(any())).thenReturn(ApiCommandRecordStore.Claim.existing(completed));
        AtomicInteger mutations = new AtomicInteger();

        CommandExecutionResult<UserView> result = service.execute(
                request(FINGERPRINT),
                mutationThatMustNotRun(mutations),
                target -> Optional.of(new UserView("Fresh authorized view")));

        assertThat(result).isInstanceOf(CommandExecutionResult.Completed.class);
        CommandExecutionResult.Completed<UserView> replayed =
                (CommandExecutionResult.Completed<UserView>) result;
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.representation()).contains(new UserView("Fresh authorized view"));
        assertThat(mutations).hasValue(0);
        verify(store, never()).complete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reusedKeyWithDifferentCanonicalCommandReturnsAuditedConflict() {
        CanonicalCommandHasher.Fingerprint changed = new CanonicalCommandHasher.Fingerprint(
                (short) 1,
                FINGERPRINT.httpMethod(),
                FINGERPRINT.routeTemplate(),
                "c".repeat(64));
        when(store.claim(any())).thenReturn(ApiCommandRecordStore.Claim.existing(completedRecord(FINGERPRINT)));
        AtomicInteger mutations = new AtomicInteger();
        AtomicInteger replays = new AtomicInteger();

        CommandExecutionResult<UserView> result = service.execute(
                request(changed),
                mutationThatMustNotRun(mutations),
                target -> {
                    replays.incrementAndGet();
                    return Optional.of(new UserView("Unexpected"));
                });

        assertThat(result).isEqualTo(new CommandExecutionResult.Conflict<UserView>(COMMAND_ID));
        assertThat(mutations).hasValue(0);
        assertThat(replays).hasValue(0);
        verify(conflictPublisher).publish(new IdempotencyConflictPublisher.Conflict(
                TENANT_ID,
                PRINCIPAL_ID,
                COMMAND_ID,
                FINGERPRINT.routeTemplate(),
                Optional.of("correlation-1")));
    }

    @Test
    void visibleInProgressRecordFailsClosedInsteadOfRunningTwice() {
        when(store.claim(any())).thenReturn(ApiCommandRecordStore.Claim.existing(inProgressRecord(FINGERPRINT)));

        assertThatThrownBy(() -> service.execute(
                        request(FINGERPRINT),
                        () -> CommandCompletion.withoutRepresentation(
                                204,
                                "USER_PROFILE",
                                RESOURCE_ID,
                                7),
                        target -> Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in-progress");
    }

    private CommandExecutionRequest request(CanonicalCommandHasher.Fingerprint fingerprint) {
        return new CommandExecutionRequest(
                TENANT_ID,
                PRINCIPAL_ID,
                IdempotencyKey.parse("request-00000001"),
                fingerprint,
                Optional.of("correlation-1"));
    }

    private ApiCommandRecord completedRecord(CanonicalCommandHasher.Fingerprint fingerprint) {
        return inProgressRecord(fingerprint).complete(200, "USER_PROFILE", RESOURCE_ID, 7);
    }

    private ApiCommandRecord inProgressRecord(CanonicalCommandHasher.Fingerprint fingerprint) {
        return ApiCommandRecord.inProgress(
                COMMAND_ID,
                TENANT_ID,
                PRINCIPAL_ID,
                fingerprint.httpMethod(),
                fingerprint.routeTemplate(),
                IdempotencyKey.parse("request-00000001").digest(),
                fingerprint.schemaVersion(),
                fingerprint.commandHash());
    }

    private Supplier<CommandCompletion<UserView>> mutationThatMustNotRun(AtomicInteger mutations) {
        return () -> {
            mutations.incrementAndGet();
            return CommandCompletion.withRepresentation(
                    200,
                    "USER_PROFILE",
                    RESOURCE_ID,
                    7,
                    new UserView("Unexpected"));
        };
    }

    private record UserView(String displayName) {}
}
