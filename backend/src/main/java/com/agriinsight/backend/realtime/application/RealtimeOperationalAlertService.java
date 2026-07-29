package com.agriinsight.backend.realtime.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.CommandCompletion;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class RealtimeOperationalAlertService {

    private static final String RESOURCE_TYPE = "REALTIME_OPERATIONAL_ALERT";

    private final PermissionEvaluator permissions;
    private final RealtimeOperationalAlertQueryStore queries;
    private final RealtimeOperationalAlertAcknowledgementStore acknowledgements;
    private final CommandExecutionService commands;
    private final Clock clock;

    @Autowired
    public RealtimeOperationalAlertService(
            PermissionEvaluator permissions,
            RealtimeOperationalAlertQueryStore queries,
            RealtimeOperationalAlertAcknowledgementStore acknowledgements,
            CommandExecutionService commands) {
        this(permissions, queries, acknowledgements, commands, Clock.systemUTC());
    }

    RealtimeOperationalAlertService(
            PermissionEvaluator permissions,
            RealtimeOperationalAlertQueryStore queries,
            RealtimeOperationalAlertAcknowledgementStore acknowledgements,
            CommandExecutionService commands,
            Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.queries = Objects.requireNonNull(queries, "queries is required");
        this.acknowledgements = Objects.requireNonNull(
                acknowledgements, "acknowledgements is required");
        this.commands = Objects.requireNonNull(commands, "commands is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public RealtimeOperationalAlertFeed feed() {
        ScopeContext scope = permissions.requireTenant(Permission.REALTIME_ALERT_READ);
        Instant generatedAt = clock.instant();
        List<RealtimeOperationalAlertView> rows =
                queries.findLatestOpen(scope.tenantId(), scope.profileId(), generatedAt);
        boolean hasMore = rows.size() > RealtimeOperationalAlertFeed.LIMIT;
        List<RealtimeOperationalAlertView> items = rows.stream()
                .limit(RealtimeOperationalAlertFeed.LIMIT)
                .toList();
        return new RealtimeOperationalAlertFeed(
                generatedAt, items, RealtimeOperationalAlertFeed.LIMIT, hasMore);
    }

    public CommandExecutionResult<RealtimeOperationalAlertView> acknowledge(
            CommandExecutionRequest request,
            UUID alertId) {
        ScopeContext scope = permissions.requireTenant(Permission.REALTIME_ALERT_ACKNOWLEDGE);
        CommandExecutionRequest requiredRequest =
                Objects.requireNonNull(request, "request is required");
        UUID requiredAlertId = Objects.requireNonNull(alertId, "alertId is required");
        requireMatchingScope(requiredRequest, scope);
        Instant acknowledgedAt = clock.instant();

        // Visibility is checked before the idempotency key is claimed.
        queries.findOpenById(
                        scope.tenantId(), scope.profileId(), requiredAlertId, acknowledgedAt)
                .orElseThrow(RealtimeOperationalAlertNotFoundException::new);

        return commands.execute(
                requiredRequest,
                () -> acknowledgeCurrent(scope, requiredAlertId, acknowledgedAt),
                target -> queries.findOpenById(
                        scope.tenantId(),
                        scope.profileId(),
                        target.resourceId(),
                        acknowledgedAt));
    }

    private CommandCompletion<RealtimeOperationalAlertView> acknowledgeCurrent(
            ScopeContext scope,
            UUID alertId,
            Instant acknowledgedAt) {
        acknowledgements.acknowledge(
                scope.tenantId(), scope.profileId(), alertId, acknowledgedAt);
        RealtimeOperationalAlertView current = queries.findOpenById(
                        scope.tenantId(), scope.profileId(), alertId, acknowledgedAt)
                .orElseThrow(RealtimeOperationalAlertNotFoundException::new);
        return CommandCompletion.withRepresentation(
                200, RESOURCE_TYPE, current.id(), current.version(), current);
    }

    private static void requireMatchingScope(
            CommandExecutionRequest request,
            ScopeContext scope) {
        if (!request.tenantId().equals(scope.tenantId())
                || !request.principalId().equals(scope.profileId())) {
            throw new AccessDeniedException("Access is denied");
        }
    }
}
