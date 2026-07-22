package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class OperatingCostService {

    private final PermissionEvaluator permissions;
    private final OperatingCostStore store;
    private final TenantAuditPublisher auditPublisher;

    public OperatingCostService(
            PermissionEvaluator permissions,
            OperatingCostStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(
                auditPublisher, "auditPublisher is required");
    }

    public OperatingCostRecord post(CostCommands.Post command, UUID commandReference) {
        ScopeContext scope = requirePostTarget(command);
        OperatingCostEntry entry = posting(
                UUID.randomUUID(), scope, command, commandReference);
        OperatingCostRecord created = store.append(scope, entry)
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Operating cost target became unavailable"));
        publish(scope, TenantAuditEvent.Action.COST_POSTED, created.id(),
                created.target().type() + ":" + created.target().id().orElse(null),
                command.audit());
        return created;
    }

    public CostCorrectionRecord correct(
            UUID originalEntryId,
            CostCommands.Correct command,
            UUID commandReference) {
        Objects.requireNonNull(command, "command is required");
        OperatingCostRecord original = getForManagement(originalEntryId);
        if (original.kind() != CostEntryKind.POSTING) {
            throw new ResourceStateConflictException("A reversal cannot be corrected");
        }
        ScopeContext scope = requireTarget(command.target());
        OperatingCostEntry reversal = reversal(
                UUID.randomUUID(), scope, original, command, commandReference);
        OperatingCostEntry replacement = posting(
                UUID.randomUUID(), scope, command, commandReference);
        CostCorrectionRecord correction = store.appendCorrection(
                        scope, original.id(), reversal, replacement)
                .orElseThrow(() -> new ResourceStateConflictException(
                        "Operating cost entry is unavailable or already corrected"));
        publish(scope, TenantAuditEvent.Action.COST_CORRECTED,
                correction.replacement().id(),
                original.id() + ":" + correction.reversal().id()
                        + ":" + correction.replacement().id(),
                command.audit());
        return correction;
    }

    ScopeContext requirePostTarget(CostCommands.Post command) {
        Objects.requireNonNull(command, "command is required");
        return requireTarget(command.target());
    }

    void requireCorrectionAccess(UUID originalEntryId, CostCommands.Correct command) {
        Objects.requireNonNull(command, "command is required");
        getForManagement(originalEntryId);
        requireTarget(command.target());
    }

    OperatingCostRecord getForManagement(UUID entryId) {
        ScopeContext scope = permissions.requireTenant(Permission.COST_MANAGE);
        return store.findById(scope, Objects.requireNonNull(entryId, "entryId is required"))
                .orElseThrow(() -> new ResourceNotFoundException("Operating cost entry"));
    }

    CostCorrectionRecord getCorrectionForReplay(UUID replacementEntryId) {
        ScopeContext scope = permissions.requireTenant(Permission.COST_MANAGE);
        return store.findCorrectionByReplacementId(scope, replacementEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Operating cost correction"));
    }

    private ScopeContext requireTarget(com.agriinsight.backend.cost.domain.CostTarget target) {
        ScopeContext scope = permissions.requireTenant(Permission.COST_MANAGE);
        if (!store.targetAvailable(scope, Objects.requireNonNull(target, "target is required"))) {
            throw new ResourceNotFoundException("Operating cost target");
        }
        return scope;
    }

    private OperatingCostEntry posting(
            UUID id, ScopeContext scope, CostCommands.Post command, UUID commandReference) {
        return new OperatingCostEntry(
                id, scope.tenantId(), command.target(), command.category(), command.amountVnd(),
                CostEntryKind.POSTING, command.occurredAt(), command.description(),
                command.sourceReference(), Optional.empty(), commandReference, scope.profileId());
    }

    private OperatingCostEntry posting(
            UUID id, ScopeContext scope, CostCommands.Correct command, UUID commandReference) {
        return new OperatingCostEntry(
                id, scope.tenantId(), command.target(), command.category(), command.amountVnd(),
                CostEntryKind.POSTING, command.occurredAt(), command.description(),
                command.sourceReference(), Optional.empty(), commandReference, scope.profileId());
    }

    private OperatingCostEntry reversal(
            UUID id,
            ScopeContext scope,
            OperatingCostRecord original,
            CostCommands.Correct command,
            UUID commandReference) {
        return new OperatingCostEntry(
                id, scope.tenantId(), original.target(), original.category(), original.amountVnd(),
                CostEntryKind.REVERSAL, original.occurredAt(),
                Optional.of(command.correctionReason()), original.sourceReference(),
                Optional.of(original.id()), commandReference, scope.profileId());
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            UUID targetId,
            String targetReference,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope, action, TenantAuditEvent.TargetType.OPERATING_COST_ENTRY,
                Optional.of(targetId), Optional.of(targetReference),
                metadata.reasonCode(), metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }
}
