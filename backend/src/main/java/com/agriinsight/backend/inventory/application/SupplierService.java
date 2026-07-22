package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.inventory.domain.Supplier;
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
public class SupplierService {

    private final PermissionEvaluator permissions;
    private final SupplierStore store;
    private final TenantAuditPublisher auditPublisher;

    public SupplierService(
            PermissionEvaluator permissions,
            SupplierStore store,
            TenantAuditPublisher auditPublisher) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher is required");
    }

    public SupplierPage list(SupplierQuery query) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE);
        return store.findAll(scope, Objects.requireNonNull(query, "query is required"));
    }

    public SupplierRecord get(UUID supplierId) {
        ScopeContext scope = permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE);
        return requiredSupplier(scope, requiredId(supplierId));
    }

    public SupplierRecord create(SupplierCommands.Create command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        Supplier supplier = new Supplier(
                UUID.randomUUID(), scope.tenantId(), command.code(), command.displayName());
        SupplierRecord created = store.create(scope, supplier);
        publish(scope, TenantAuditEvent.Action.SUPPLIER_CREATED, created, command.audit());
        return created;
    }

    public SupplierRecord update(UUID supplierId, SupplierCommands.Update command) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        UUID target = requiredId(supplierId);
        requiredSupplier(scope, target);
        Optional<SupplierRecord> updated = store.update(
                scope, target, command.expectedVersion(), command);
        if (updated.isPresent()) {
            SupplierRecord supplier = updated.orElseThrow();
            publish(scope, TenantAuditEvent.Action.SUPPLIER_UPDATED, supplier, command.audit());
            return supplier;
        }
        return failedMutation(scope, target, command.expectedVersion());
    }

    public SupplierRecord deactivate(UUID supplierId, SupplierCommands.Lifecycle command) {
        return changeActive(supplierId, command, false);
    }

    public SupplierRecord reactivate(UUID supplierId, SupplierCommands.Lifecycle command) {
        return changeActive(supplierId, command, true);
    }

    ScopeContext requireTenantManagement() {
        return permissions.requireTenant(Permission.INVENTORY_MANAGE);
    }

    SupplierRecord getForTenantManagement(UUID supplierId) {
        return requiredSupplier(requireTenantManagement(), requiredId(supplierId));
    }

    private SupplierRecord changeActive(
            UUID supplierId,
            SupplierCommands.Lifecycle command,
            boolean active) {
        Objects.requireNonNull(command, "command is required");
        ScopeContext scope = requireTenantManagement();
        UUID target = requiredId(supplierId);
        requiredSupplier(scope, target);
        Optional<SupplierRecord> updated = store.updateActive(
                scope, target, command.expectedVersion(), active);
        if (updated.isPresent()) {
            SupplierRecord supplier = updated.orElseThrow();
            publish(
                    scope,
                    active ? TenantAuditEvent.Action.SUPPLIER_REACTIVATED
                            : TenantAuditEvent.Action.SUPPLIER_DEACTIVATED,
                    supplier,
                    command.audit());
            return supplier;
        }
        return failedLifecycleMutation(scope, target, command.expectedVersion(), active);
    }

    private SupplierRecord failedMutation(
            ScopeContext scope, UUID supplierId, long expectedVersion) {
        SupplierRecord current = requiredSupplier(scope, supplierId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        throw new ResourceStateConflictException("Supplier update does not change state");
    }

    private SupplierRecord failedLifecycleMutation(
            ScopeContext scope,
            UUID supplierId,
            long expectedVersion,
            boolean active) {
        SupplierRecord current = requiredSupplier(scope, supplierId);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, current.version());
        }
        if (!active && current.active() && store.hasReferences(scope, supplierId)) {
            throw new ResourceStateConflictException("Supplier has inventory references");
        }
        throw new ResourceStateConflictException(
                active ? "Supplier is already active" : "Supplier is already inactive");
    }

    private SupplierRecord requiredSupplier(ScopeContext scope, UUID supplierId) {
        return store.findById(scope, supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier"));
    }

    private void publish(
            ScopeContext scope,
            TenantAuditEvent.Action action,
            SupplierRecord supplier,
            TenantAuditMetadata metadata) {
        auditPublisher.publish(new TenantAuditEvent(
                scope,
                action,
                TenantAuditEvent.TargetType.SUPPLIER,
                Optional.of(supplier.id()),
                Optional.of(supplier.code()),
                metadata.reasonCode(),
                metadata.correlationId(),
                TenantAuditEvent.Outcome.SUCCEEDED));
    }

    private UUID requiredId(UUID supplierId) {
        return Objects.requireNonNull(supplierId, "supplierId is required");
    }
}
