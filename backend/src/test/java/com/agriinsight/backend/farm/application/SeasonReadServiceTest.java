package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.SEASON_ID;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.season;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeasonReadServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final SeasonStore store = mock(SeasonStore.class);
    private SeasonService service;

    @BeforeEach
    void createService() {
        service = new SeasonService(permissions, store, mock(TenantAuditPublisher.class));
        when(permissions.requireDomainList(Permission.SEASON_READ, ScopeContext.Type.FARM))
                .thenReturn(LIST_SCOPE);
    }

    @Test
    void appliesParentFarmScopeBeforePagingAndMaterialization() {
        SeasonQuery query = new SeasonQuery(
                25, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Season.Status.ACTIVE), Optional.empty());
        SeasonPage expected = new SeasonPage(List.of(season(2, Season.Status.ACTIVE)), 25, 0, false);
        when(store.findAll(LIST_SCOPE, query)).thenReturn(expected);

        assertThat(service.list(query)).isEqualTo(expected);

        verify(store).findAll(LIST_SCOPE, query);
    }

    @Test
    void hiddenOrUnknownSeasonReturnsNotFoundAfterScopedLookup() {
        when(store.findById(LIST_SCOPE, SEASON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SEASON_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Season");
    }
}
