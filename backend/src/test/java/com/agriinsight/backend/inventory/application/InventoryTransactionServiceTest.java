package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryTransactionServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final UUID SUPPLIER_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION_ID = UUID.fromString("58000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final ScopeContext TARGET_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.of(WAREHOUSE_ID));
    private static final ScopeContext LIST_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.empty());
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final InventoryTransactionStore store = mock(InventoryTransactionStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private InventoryTransactionService service;

    @BeforeEach
    void createService() {
        when(permissions.requireDomain(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE, WAREHOUSE_ID))
                .thenReturn(TARGET_SCOPE);
        when(permissions.requireDomainList(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE))
                .thenReturn(LIST_SCOPE);
        service = new InventoryTransactionService(permissions, store, auditPublisher);
    }

    @Test
    void receiptUsesTargetWarehouseScopeAndPublishesTypedAudit() {
        var command = receipt();
        when(store.postingTargetAvailable(TARGET_SCOPE, command)).thenReturn(true);
        when(store.post(any(), any(), any())).thenAnswer(invocation ->
                receiptRecord(invocation.getArgument(1), 0));

        InventoryTransactionRecord posted = service.post(command);

        assertThat(posted.kind()).isEqualTo(InventoryTransactionKind.RECEIPT);
        verify(store).post(TARGET_SCOPE, posted.id(), command);
        org.mockito.ArgumentCaptor<TenantAuditEvent> event =
                org.mockito.ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action())
                .isEqualTo(TenantAuditEvent.Action.INVENTORY_RECEIPT_POSTED);
        assertThat(event.getValue().targetType())
                .isEqualTo(TenantAuditEvent.TargetType.INVENTORY_TRANSACTION);
    }

    @Test
    void unavailableTargetFailsBeforeLedgerMutation() {
        var command = receipt();

        assertThatThrownBy(() -> service.post(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active inventory posting target");

        verify(store, never()).post(any(), any(), any());
    }

    @Test
    void reversalOfReversalIsRejectedBeforeMutation() {
        var command = new InventoryTransactionCommands.Reversal(
                BigDecimal.ONE, "Correction", 0, AUDIT);
        when(store.findById(LIST_SCOPE, TRANSACTION_ID))
                .thenReturn(Optional.of(reversalRecord()));

        assertThatThrownBy(() -> service.reverse(TRANSACTION_ID, command))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessage("A reversal cannot be reversed");

        verify(store, never()).reverse(any(), any(), any(), any());
    }

    @Test
    void staleReversalVersionReturnsTypedConflict() {
        var command = new InventoryTransactionCommands.Reversal(
                BigDecimal.ONE, "Correction", 1, AUDIT);
        when(store.findById(LIST_SCOPE, TRANSACTION_ID))
                .thenReturn(Optional.of(receiptRecord(2)));

        assertThatThrownBy(() -> service.reverse(TRANSACTION_ID, command))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(1);
                    assertThat(conflict.currentVersion()).isEqualTo(2);
                });

        verify(store, never()).reverse(any(), any(), any(), any());
    }

    private InventoryTransactionCommands.Receipt receipt() {
        return new InventoryTransactionCommands.Receipt(
                WAREHOUSE_ID, MATERIAL_ID, SUPPLIER_ID,
                BigDecimal.ONE, new BigDecimal("100"), "BATCH-A",
                LocalDate.parse("2027-12-31"), Instant.parse("2027-01-01T08:00:00Z"),
                Optional.empty(), AUDIT);
    }

    private InventoryTransactionRecord receiptRecord(long version) {
        return receiptRecord(TRANSACTION_ID, version);
    }

    private InventoryTransactionRecord receiptRecord(UUID transactionId, long version) {
        return new InventoryTransactionRecord(
                transactionId, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID,
                InventoryTransactionKind.RECEIPT, CanonicalUnit.KG,
                BigDecimal.ONE, BigDecimal.ONE, Optional.of(new BigDecimal("100")),
                new BigDecimal("100"), Optional.of(SUPPLIER_ID), Optional.of("BATCH-A"),
                Optional.of(LocalDate.parse("2027-12-31")),
                Instant.parse("2027-01-01T08:00:00Z"), Optional.empty(), Optional.empty(),
                Optional.empty(), PROFILE_ID, version);
    }

    private InventoryTransactionRecord reversalRecord() {
        return new InventoryTransactionRecord(
                TRANSACTION_ID, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID,
                InventoryTransactionKind.REVERSAL, CanonicalUnit.KG,
                BigDecimal.ONE, BigDecimal.ONE.negate(), Optional.empty(), BigDecimal.ZERO,
                Optional.empty(), Optional.empty(), Optional.empty(), Instant.now(),
                Optional.of("Correction"), Optional.empty(), Optional.of(UUID.randomUUID()),
                PROFILE_ID, 0);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
