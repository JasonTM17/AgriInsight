package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import com.agriinsight.backend.shared.application.CommandTarget;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RealtimeOperationalAlertServiceTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000071");
    private static final UUID PROFILE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000071");
    private static final UUID ALERT_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2027-09-01T03:00:00Z");
    private static final ScopeContext SCOPE = new ScopeContext(
            TENANT_ID, PROFILE_ID, ScopeContext.Type.TENANT, Optional.empty());

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final RealtimeOperationalAlertQueryStore queries =
            mock(RealtimeOperationalAlertQueryStore.class);
    private final RealtimeOperationalAlertAcknowledgementStore acknowledgements =
            mock(RealtimeOperationalAlertAcknowledgementStore.class);
    private final CommandExecutionService commands = mock(CommandExecutionService.class);
    private final RealtimeOperationalAlertService service =
            new RealtimeOperationalAlertService(
                    permissions,
                    queries,
                    acknowledgements,
                    commands,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void resolvesPermissionBeforeReadingTheFixedLookaheadWindow() {
        when(permissions.requireTenant(Permission.REALTIME_ALERT_READ)).thenReturn(SCOPE);
        when(queries.findLatestOpen(TENANT_ID, PROFILE_ID, NOW))
                .thenReturn(Collections.nCopies(51, view(true)));

        RealtimeOperationalAlertFeed feed = service.feed();

        assertThat(feed.generatedAt()).isEqualTo(NOW);
        assertThat(feed.limit()).isEqualTo(50);
        assertThat(feed.items()).hasSize(50);
        assertThat(feed.hasMore()).isTrue();
        InOrder order = inOrder(permissions, queries);
        order.verify(permissions).requireTenant(Permission.REALTIME_ALERT_READ);
        order.verify(queries).findLatestOpen(TENANT_ID, PROFILE_ID, NOW);
    }

    @Test
    void deniedReadDoesNotTouchTheQueryOrCommandStores() {
        TenantAuthorizationDeniedException denied =
                mock(TenantAuthorizationDeniedException.class);
        when(permissions.requireTenant(Permission.REALTIME_ALERT_READ)).thenThrow(denied);

        assertThatThrownBy(service::feed).isSameAs(denied);

        verifyNoInteractions(queries, acknowledgements, commands);
    }

    @Test
    void acknowledgesOnlyAfterPermissionAndVisibilityThenReturnsTheCurrentProjection() {
        CommandExecutionRequest request = request();
        RealtimeOperationalAlertView current = view(true);
        when(permissions.requireTenant(Permission.REALTIME_ALERT_ACKNOWLEDGE))
                .thenReturn(SCOPE);
        when(queries.findOpenById(TENANT_ID, PROFILE_ID, ALERT_ID, NOW))
                .thenReturn(Optional.of(view(false)), Optional.of(current));
        when(commands.execute(eq(request), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<CommandCompletion<RealtimeOperationalAlertView>> mutation =
                    invocation.getArgument(1);
            CommandCompletion<RealtimeOperationalAlertView> completion = mutation.get();
            return new CommandExecutionResult.Completed<>(
                    UUID.randomUUID(),
                    false,
                    completion.responseStatus(),
                    completion.target(),
                    completion.representation());
        });

        CommandExecutionResult<RealtimeOperationalAlertView> result =
                service.acknowledge(request, ALERT_ID);

        assertThat(result).isInstanceOf(CommandExecutionResult.Completed.class);
        var completed =
                (CommandExecutionResult.Completed<RealtimeOperationalAlertView>) result;
        assertThat(completed.responseStatus()).isEqualTo(200);
        assertThat(completed.representation()).containsSame(current);
        verify(acknowledgements).acknowledge(TENANT_ID, PROFILE_ID, ALERT_ID, NOW);
        InOrder order = inOrder(permissions, queries, commands, acknowledgements);
        order.verify(permissions).requireTenant(Permission.REALTIME_ALERT_ACKNOWLEDGE);
        order.verify(queries).findOpenById(TENANT_ID, PROFILE_ID, ALERT_ID, NOW);
        order.verify(commands).execute(eq(request), any(), any());
        order.verify(acknowledgements).acknowledge(TENANT_ID, PROFILE_ID, ALERT_ID, NOW);
        order.verify(queries).findOpenById(TENANT_ID, PROFILE_ID, ALERT_ID, NOW);
    }

    @Test
    void replayReloadsCurrentStateAndCanReturnEmptyAfterResolution() {
        CommandExecutionRequest request = request();
        CommandTarget target =
                new CommandTarget("REALTIME_OPERATIONAL_ALERT", ALERT_ID, 7);
        when(permissions.requireTenant(Permission.REALTIME_ALERT_ACKNOWLEDGE))
                .thenReturn(SCOPE);
        when(queries.findOpenById(TENANT_ID, PROFILE_ID, ALERT_ID, NOW))
                .thenReturn(Optional.of(view(false)), Optional.empty());
        when(commands.execute(eq(request), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<CommandTarget, Optional<RealtimeOperationalAlertView>> replay =
                    invocation.getArgument(2);
            return new CommandExecutionResult.Completed<>(
                    UUID.randomUUID(), true, 200, target, replay.apply(target));
        });

        var result = service.acknowledge(request, ALERT_ID);

        var completed =
                (CommandExecutionResult.Completed<RealtimeOperationalAlertView>) result;
        assertThat(completed.replayed()).isTrue();
        assertThat(completed.representation()).isEmpty();
        verifyNoInteractions(acknowledgements);
    }

    @Test
    void deniedAcknowledgementDoesNotInspectVisibilityOrIdempotency() {
        TenantAuthorizationDeniedException denied =
                mock(TenantAuthorizationDeniedException.class);
        when(permissions.requireTenant(Permission.REALTIME_ALERT_ACKNOWLEDGE))
                .thenThrow(denied);

        assertThatThrownBy(() -> service.acknowledge(request(), ALERT_ID))
                .isSameAs(denied);

        verifyNoInteractions(queries, acknowledgements, commands);
    }

    @Test
    void safeEvidenceAndAcknowledgementTimeMustRemainConsistent() {
        UUID sourceEventId = UUID.randomUUID();
        assertThat(RealtimeOperationalAlertEvidence.from(
                        RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, null))
                .extracting(RealtimeOperationalAlertEvidence::type)
                .isEqualTo(RealtimeOperationalAlertEvidence.Type.TENANT_BACKLOG);
        assertThat(RealtimeOperationalAlertEvidence.from(
                        RealtimeOperationalAlertPolicy.REALTIME_DLT_RECORD, sourceEventId)
                .id())
                .contains(sourceEventId);
        assertThatThrownBy(() -> new RealtimeOperationalAlertView(
                        ALERT_ID,
                        RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                        RealtimeOperationalAlertSeverity.WARNING,
                        RealtimeOperationalAlertView.OPEN,
                        RealtimeOperationalAlertEvidence.from(
                                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, null),
                        NOW.minusSeconds(120),
                        NOW.minusSeconds(120),
                        NOW.minusSeconds(30),
                        NOW,
                        30,
                        true,
                        Optional.empty(),
                        7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CommandExecutionRequest request() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        when(request.tenantId()).thenReturn(TENANT_ID);
        when(request.principalId()).thenReturn(PROFILE_ID);
        return request;
    }

    private RealtimeOperationalAlertView view(boolean acknowledged) {
        return new RealtimeOperationalAlertView(
                ALERT_ID,
                RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG,
                RealtimeOperationalAlertSeverity.WARNING,
                RealtimeOperationalAlertView.OPEN,
                RealtimeOperationalAlertEvidence.from(
                        RealtimeOperationalAlertPolicy.OUTBOX_PUBLISH_BACKLOG, null),
                NOW.minusSeconds(120),
                NOW.minusSeconds(120),
                NOW.minusSeconds(30),
                NOW,
                30,
                acknowledged,
                acknowledged ? Optional.of(NOW.minusSeconds(5)) : Optional.empty(),
                7);
    }
}
