package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FIELD_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.PROFILE_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.SEASON_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HarvestServiceTest {

    private static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    private static final ScopeContext SCOPE = ScopeContext.domain(
            ActivityApplicationTestFixtures.PRINCIPAL,
            ScopeContext.Type.FARM,
            Optional.of(FARM_ID));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final HarvestStore store = mock(HarvestStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private HarvestService service;

    @BeforeEach
    void createService() {
        when(permissions.requireDomain(Permission.HARVEST_MANAGE, ScopeContext.Type.FARM, FARM_ID))
                .thenReturn(SCOPE);
        when(store.farmVisible(SCOPE, FARM_ID)).thenReturn(true);
        service = new HarvestService(permissions, store, auditPublisher);
    }

    @Test
    void postUsesScopedHierarchyAndPublishesAudit() {
        HarvestCommands.Post command = postCommand();
        when(store.postTargetAvailable(
                SCOPE, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID, command.occurredOn()))
                .thenReturn(true);
        when(store.append(any(), any(Harvest.class))).thenAnswer(invocation ->
                Optional.of(record(invocation.getArgument(1))));

        HarvestRecord created = service.post(command);

        assertThat(created.quantityKg()).isEqualByComparingTo("100");
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.HARVEST_POSTED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.HARVEST);
    }

    @Test
    void unavailableHierarchyFailsBeforeAppend() {
        HarvestCommands.Post command = postCommand();
        when(store.postTargetAvailable(
                SCOPE, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID, command.occurredOn()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.post(command))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("season hierarchy");

        verify(store, never()).append(any(), any());
        verify(auditPublisher, never()).publish(any());
    }

    private HarvestCommands.Post postCommand() {
        return new HarvestCommands.Post(
                FARM_ID, FIELD_ID, SEASON_ID, CROP_ID, LocalDate.parse("2027-09-01"),
                new BigDecimal("100"), new BigDecimal("2"), Optional.of("A"),
                Optional.of(new BigDecimal("2500000")), AUDIT);
    }

    private HarvestRecord record(Harvest harvest) {
        return new HarvestRecord(
                harvest.id(), TENANT_ID, harvest.farmId(), harvest.fieldId(),
                harvest.seasonId(), harvest.cropId(), PROFILE_ID, harvest.occurredOn(),
                harvest.quantityKg(), harvest.wasteQuantityKg(), harvest.qualityGrade(),
                harvest.revenueVnd(), harvest.correctsHarvestId(), harvest.correctionKind(),
                harvest.correctionReason(), 0);
    }
}
