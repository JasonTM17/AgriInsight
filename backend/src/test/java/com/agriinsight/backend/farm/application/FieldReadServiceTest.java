package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.FIELD_ID;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.field;
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

class FieldReadServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final FieldStore store = mock(FieldStore.class);
    private FieldService service;

    @BeforeEach
    void createService() {
        service = new FieldService(permissions, store, mock(TenantAuditPublisher.class));
        when(permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM))
                .thenReturn(LIST_SCOPE);
    }

    @Test
    void appliesFarmScopeBeforePagingAndMaterialization() {
        FieldQuery query = new FieldQuery(
                25, 0, Optional.empty(), Optional.of(true), Optional.empty());
        FieldPage expected = new FieldPage(List.of(field(2, true)), 25, 0, false);
        when(store.findAll(LIST_SCOPE, query)).thenReturn(expected);

        assertThat(service.list(query)).isEqualTo(expected);

        verify(store).findAll(LIST_SCOPE, query);
    }

    @Test
    void hiddenOrUnknownFieldReturnsNotFoundAfterScopedLookup() {
        when(store.findById(LIST_SCOPE, FIELD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(FIELD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Field");
    }
}
