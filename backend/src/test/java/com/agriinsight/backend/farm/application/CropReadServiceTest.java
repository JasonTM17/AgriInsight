package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.CROP_ID;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.crop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CropReadServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final CropStore store = mock(CropStore.class);
    private CropService service;

    @BeforeEach
    void createService() {
        service = new CropService(permissions, store, mock(TenantAuditPublisher.class));
        when(permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM))
                .thenReturn(LIST_SCOPE);
    }

    @Test
    void appliesAssignmentGuardBeforeCatalogPaging() {
        CropQuery query = new CropQuery(25, 0, Optional.of(true), Optional.empty());
        CropPage expected = new CropPage(List.of(crop(2, true)), 25, 0, false);
        when(store.findAll(LIST_SCOPE, query)).thenReturn(expected);

        assertThat(service.list(query)).isEqualTo(expected);

        verify(store).findAll(LIST_SCOPE, query);
    }

    @Test
    void hiddenOrUnknownCropReturnsNotFoundAfterScopedLookup() {
        when(store.findById(LIST_SCOPE, CROP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CROP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Crop");
    }
}
