package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.CROP_ID;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.TENANT_ID;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.TENANT_SCOPE;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.crop;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.updateCommand;
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
import com.agriinsight.backend.farm.domain.Crop;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CropMutationServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final CropStore store = mock(CropStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private CropService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.FARM_MANAGE)).thenReturn(TENANT_SCOPE);
        service = new CropService(permissions, store, auditPublisher);
    }

    @Test
    void createsOnlyWithTenantWideManagementAndPublishesAudit() {
        when(store.create(any(), any(Crop.class))).thenAnswer(invocation -> {
            Crop created = invocation.getArgument(1);
            return new CropRecord(
                    created.id(), TENANT_ID, created.code(), created.displayName(),
                    created.scientificName(), true, 0);
        });

        CropRecord created = service.create(createCommand());

        assertThat(created.active()).isTrue();
        verify(permissions).requireTenant(Permission.FARM_MANAGE);
        verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }

    @Test
    void stalePatchReturnsTypedConflictWithoutSuccessAudit() {
        when(store.findById(TENANT_SCOPE, CROP_ID))
                .thenReturn(Optional.of(crop(2, true)))
                .thenReturn(Optional.of(crop(3, true)));
        when(store.update(TENANT_SCOPE, CROP_ID, 2, updateCommand(2))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(CROP_ID, updateCommand(2)))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void deactivationReportsLiveSeasonBlockers() {
        when(store.findById(TENANT_SCOPE, CROP_ID))
                .thenReturn(Optional.of(crop(4, true)));
        when(store.updateActive(TENANT_SCOPE, CROP_ID, 4, false)).thenReturn(Optional.empty());
        when(store.hasDeactivationBlockers(TENANT_SCOPE, CROP_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(
                CROP_ID, new CropCommands.Lifecycle(4, CropApplicationTestFixtures.AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("live seasons");
    }
}
