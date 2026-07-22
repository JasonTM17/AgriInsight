package com.agriinsight.backend.cost.application;

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
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperatingCostServiceTest {

    private static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000005");
    private static final UUID ORIGINAL_ID = UUID.fromString(
            "63000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_REFERENCE = UUID.fromString(
            "63000000-0000-0000-0000-000000000010");
    private static final ScopeContext SCOPE = new ScopeContext(
            TENANT_ID, PROFILE_ID, ScopeContext.Type.TENANT, Optional.empty());
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("COST_CHANGE"), Optional.of("request-cost-01"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final OperatingCostStore store = mock(OperatingCostStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private OperatingCostService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.COST_MANAGE)).thenReturn(SCOPE);
        service = new OperatingCostService(permissions, store, auditPublisher);
    }

    @Test
    void postValidatesTargetBeforeAppendingAndAuditsTheLedgerEntry() {
        CostCommands.Post command = post(CostTarget.domain(CostTarget.Type.FARM, FARM_ID));
        when(store.targetAvailable(SCOPE, command.target())).thenReturn(true);
        when(store.append(any(), any())).thenAnswer(invocation -> Optional.of(
                record(invocation.getArgument(1))));

        OperatingCostRecord created = service.post(command, COMMAND_REFERENCE);

        assertThat(created.amountVnd()).isEqualByComparingTo("1250000");
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.COST_POSTED);
        assertThat(event.getValue().targetType())
                .isEqualTo(TenantAuditEvent.TargetType.OPERATING_COST_ENTRY);
    }

    @Test
    void missingCanonicalTargetIsHiddenBeforeAppend() {
        CostCommands.Post command = post(CostTarget.domain(CostTarget.Type.FARM, FARM_ID));

        assertThatThrownBy(() -> service.post(command, COMMAND_REFERENCE))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(store, never()).append(any(), any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void correctionCopiesOriginalDimensionsAndCreatesASeparateReplacement() {
        CostTarget originalTarget = CostTarget.domain(CostTarget.Type.FARM, FARM_ID);
        OperatingCostRecord original = record(new OperatingCostEntry(
                ORIGINAL_ID, TENANT_ID, originalTarget, CostCategory.LABOR,
                new BigDecimal("500000"), CostEntryKind.POSTING,
                Instant.parse("2027-08-01T00:00:00Z"), Optional.of("Original"),
                Optional.of("PAYROLL-08"), Optional.empty(),
                UUID.fromString("63000000-0000-0000-0000-000000000011"), PROFILE_ID));
        CostTarget correctedTarget = CostTarget.domain(
                CostTarget.Type.FIELD,
                UUID.fromString("41000000-0000-0000-0000-000000000003"));
        CostCommands.Correct command = correct(correctedTarget);
        when(store.findById(SCOPE, ORIGINAL_ID)).thenReturn(Optional.of(original));
        when(store.targetAvailable(SCOPE, correctedTarget)).thenReturn(true);
        when(store.appendCorrection(any(), any(), any(), any())).thenAnswer(invocation ->
                Optional.of(new CostCorrectionRecord(
                        record(invocation.getArgument(2)),
                        record(invocation.getArgument(3)))));

        CostCorrectionRecord result = service.correct(
                ORIGINAL_ID, command, COMMAND_REFERENCE);

        assertThat(result.reversal().target()).isEqualTo(originalTarget);
        assertThat(result.reversal().category()).isEqualTo(CostCategory.LABOR);
        assertThat(result.reversal().amountVnd()).isEqualByComparingTo("500000");
        assertThat(result.reversal().occurredAt()).isEqualTo(original.occurredAt());
        assertThat(result.replacement().target()).isEqualTo(correctedTarget);
        assertThat(result.replacement().category()).isEqualTo(CostCategory.MATERIAL);
        assertThat(result.replacement().signedAmountVnd()).isPositive();
        assertThat(result.reversal().signedAmountVnd()).isNegative();
    }

    private CostCommands.Post post(CostTarget target) {
        return new CostCommands.Post(
                target, CostCategory.LABOR, new BigDecimal("1250000"),
                Instant.parse("2027-09-01T02:00:00Z"), Optional.of("Workers"),
                Optional.of("PAYROLL-09"), AUDIT);
    }

    private CostCommands.Correct correct(CostTarget target) {
        return new CostCommands.Correct(
                target, CostCategory.MATERIAL, new BigDecimal("750000"),
                Instant.parse("2027-09-02T02:00:00Z"), Optional.of("Corrected"),
                Optional.of("INVOICE-09"), "Correct target and category", AUDIT);
    }

    private OperatingCostRecord record(OperatingCostEntry entry) {
        return new OperatingCostRecord(
                entry.id(), entry.tenantId(), entry.target(), entry.category(),
                entry.amountVnd(), entry.kind(), entry.occurredAt(), entry.description(),
                entry.sourceReference(), entry.reversalOf(), entry.commandReference(),
                entry.recordedByProfileId(), 0);
    }
}
