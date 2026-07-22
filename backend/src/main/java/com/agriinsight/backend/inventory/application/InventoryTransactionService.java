package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
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
public class InventoryTransactionService {

    private final PermissionEvaluator permissions;
    private final InventoryTransactionStore store;
    private final TenantAuditPublisher auditPublisher;

    public InventoryTransactionService(
            PermissionEvaluator permissions,
            InventoryTransactionStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public InventoryTransactionRecord post(InventoryTransactionCommands.Posting command) {
        ScopeContext scope = requirePostingTarget(command);
        InventoryTransactionRecord transaction = store.post(scope, UUID.randomUUID(), command);
        publish(
                scope,
                command.kind() == InventoryTransactionKind.RECEIPT
                        ? TenantAuditEvent.Action.INVENTORY_RECEIPT_POSTED
                        : TenantAuditEvent.Action.INVENTORY_ISSUE_POSTED,
                transaction,
                command.audit());
        return transaction;
    }

    public InventoryTransactionRecord reverse(
            UUID transactionId,
            InventoryTransactionCommands.Reversal command) {
        Objects.requireNonNull(command, "command is required");
        InventoryTransactionRecord original = requireReversalTarget(transactionId, command);
        ScopeContext scope = requireWarehouseManagement(original.warehouseId());
        InventoryTransactionRecord reversal = store.reverse(
                scope, original.id(), UUID.randomUUID(), command);
        publish(
                scope,
                TenantAuditEvent.Action.INVENTORY_REVERSAL_POSTED,
                reversal,
                command.audit());
        return reversal;
    }

    ScopeContext requirePostingTarget(InventoryTransactionCommands.Posting command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireWarehouseManagement(command.warehouseId());
        if (!store.postingTargetAvailable(scope, command)) {
            throw new ResourceNotFoundException("Active inventory posting target");
        }
        return scope;
    }

    InventoryTransactionRecord requireReversalTarget(
            UUID transactionId,
            InventoryTransactionCommands.Reversal command) {
        Objects.requireNonNull(command, "command is required");
        InventoryTransactionRecord original = requireReversalAccess(transactionId);
        if (original.version() != command.expectedVersion()) {
            throw new VersionConflictException(command.expectedVersion(), original.version());
        }
        return original;
    }

    InventoryTransactionRecord requireReversalAccess(UUID transactionId) {
        InventoryTransactionRecord original = getForManagement(transactionId);
        if (original.kind() == InventoryTransactionKind.REVERSAL) {
            throw new ResourceStateConflictException("A reversal cannot be reversed");
        }
        return original;
    }

    InventoryTransactionRecord getForManagement(UUID transactionId) {
        ScopeContext listScope = permissions.requireDomainList(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE);
        InventoryTransactionRecord transaction = store.findById(
                        listScope, Objects.requireNonNull(transactionId, "transactionId is required"))
                .orElseThrow(() -> new ResourceNotFoundException("Inventory transaction"));
        requireWarehouseManagement(transaction.warehouseId());
        return transaction;
    }

    InventoryTransactionRecord getForReplay(UUID transactionId) {
        return getForManagement(transactionId);
    }

    private ScopeContext requireWarehouseManagement(UUID warehouseId) {
        return permissions.requireDomain(
                Permission.INVENTORY_MANAGE,
                ScopeContext.Type.WAREHOUSE,
                Objects.requireNonNull(warehouseId, "warehouseId is required"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            InventoryTransactionRecord transaction,
            com.agriinsight.backend.authorization.application.TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.INVENTORY_TRANSACTION,
                Optional.of(transaction.id()),
                Optional.of(transaction.kind() + ":" + transaction.warehouseId()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }
}
