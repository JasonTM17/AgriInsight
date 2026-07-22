package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.FARM_SCOPE;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.FIELD_ID;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.TENANT_ID;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.field;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.updateCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Field;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FieldMutationServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final FieldStore store = mock(FieldStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private FieldService service;

    @BeforeEach
    void createService() {
        when(permissions.requireDomainList(Permission.FARM_MANAGE, ScopeContext.Type.FARM))
                .thenReturn(LIST_SCOPE);
        when(permissions.requireDomain(Permission.FARM_MANAGE, ScopeContext.Type.FARM, FARM_ID))
                .thenReturn(FARM_SCOPE);
        when(store.farmVisible(FARM_SCOPE, FARM_ID)).thenReturn(true);
        service = new FieldService(permissions, store, auditPublisher);
    }

    @Test
    void createsOnlyWithAvailableParentsAndPublishesAudit() {
        when(store.liveParentsAvailable(any(), any(), any())).thenReturn(true);
        when(store.create(any(), any(Field.class))).thenAnswer(invocation -> {
            Field created = invocation.getArgument(1);
            return new FieldRecord(
                    created.id(), TENANT_ID, created.farmId(), created.code(), created.displayName(),
                    created.areaHectares(), created.responsibleEmployeeId(), created.coordinates(),
                    created.soilType(), created.irrigationType(), true, 0);
        });

        FieldRecord created = service.create(createCommand());

        assertThat(created.active()).isTrue();
        verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }

    @Test
    void stalePatchReturnsTypedConflictWithoutSuccessAudit() {
        when(store.findById(LIST_SCOPE, FIELD_ID)).thenReturn(Optional.of(field(2, true)));
        when(store.liveParentsAvailable(any(), any(), any())).thenReturn(true);
        when(store.update(FARM_SCOPE, FIELD_ID, 2, updateCommand(2))).thenReturn(Optional.empty());
        when(store.findById(FARM_SCOPE, FIELD_ID)).thenReturn(Optional.of(field(3, true)));

        assertThatThrownBy(() -> service.update(FIELD_ID, updateCommand(2)))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void deactivationReportsLiveOperationalBlockers() {
        when(store.findById(LIST_SCOPE, FIELD_ID)).thenReturn(Optional.of(field(4, true)));
        when(store.updateActive(FARM_SCOPE, FIELD_ID, 4, false)).thenReturn(Optional.empty());
        when(store.findById(FARM_SCOPE, FIELD_ID)).thenReturn(Optional.of(field(4, true)));
        when(store.hasDeactivationBlockers(FARM_SCOPE, FIELD_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(
                FIELD_ID, new FieldCommands.Lifecycle(4, FieldApplicationTestFixtures.AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("live seasons or activities");
    }

    @Test
    void reactivationRequiresLiveParentsBeforeMutation() {
        when(store.findById(LIST_SCOPE, FIELD_ID)).thenReturn(Optional.of(field(5, false)));
        when(store.liveParentsAvailable(FARM_SCOPE, FARM_ID, field(5, false).responsibleEmployeeId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.reactivate(
                FIELD_ID, new FieldCommands.Lifecycle(5, FieldApplicationTestFixtures.AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("active farm");

        verify(store, never()).updateActive(any(), any(), anyLong(), anyBoolean());
    }
}
