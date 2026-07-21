package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.AUDIT;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.FARM_SCOPE;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.SEASON_ID;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.TENANT_ID;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.season;
import static com.agriinsight.backend.farm.application.SeasonApplicationTestFixtures.updateCommand;
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
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SeasonMutationServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final SeasonStore store = mock(SeasonStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private SeasonService service;

    @BeforeEach
    void createService() {
        when(permissions.requireDomainList(Permission.SEASON_MANAGE, ScopeContext.Type.FARM))
                .thenReturn(LIST_SCOPE);
        when(permissions.requireDomain(Permission.SEASON_MANAGE, ScopeContext.Type.FARM, FARM_ID))
                .thenReturn(FARM_SCOPE);
        service = new SeasonService(permissions, store, auditPublisher);
    }

    @Test
    void createsOnlyWithAvailableLiveParentsAndPublishesAudit() {
        when(store.liveParentsAvailable(any(), any(), any(), any(), any())).thenReturn(true);
        when(store.create(any(), any(Season.class))).thenAnswer(invocation -> {
            Season created = invocation.getArgument(1);
            return new SeasonRecord(
                    created.id(), TENANT_ID, created.farmId(), created.fieldId(), created.cropId(),
                    created.code(), created.displayName(), created.varietyName(),
                    created.plannedStartDate(), created.plannedEndDate(), Optional.empty(), Optional.empty(),
                    created.plantedAreaHectares(), created.budgetVnd(), Season.Status.PLANNED, 0);
        });

        SeasonRecord created = service.create(createCommand());

        assertThat(created.status()).isEqualTo(Season.Status.PLANNED);
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.SEASON_CREATED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.SEASON);
    }

    @Test
    void stalePatchReturnsTypedConflictWithoutSuccessAudit() {
        when(store.findById(LIST_SCOPE, SEASON_ID)).thenReturn(Optional.of(season(2, Season.Status.PLANNED)));
        when(store.liveParentsAvailable(any(), any(), any(), any(), any())).thenReturn(true);
        when(store.update(FARM_SCOPE, SEASON_ID, 2, updateCommand(2))).thenReturn(Optional.empty());
        when(store.findById(FARM_SCOPE, SEASON_ID)).thenReturn(Optional.of(season(3, Season.Status.PLANNED)));

        assertThatThrownBy(() -> service.update(SEASON_ID, updateCommand(2)))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void terminalMetadataAndReverseTransitionsAreRejected() {
        when(store.findById(LIST_SCOPE, SEASON_ID))
                .thenReturn(Optional.of(season(4, Season.Status.COMPLETED)));

        assertThatThrownBy(() -> service.update(SEASON_ID, updateCommand(4)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> service.transition(
                SEASON_ID,
                new SeasonCommands.Transition(
                        Season.Status.ACTIVE, LocalDate.parse("2027-12-01"), 4, AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("not allowed");
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void plannedSeasonTransitionsToActiveWithOptimisticVersion() {
        SeasonRecord planned = season(0, Season.Status.PLANNED);
        SeasonRecord active = season(1, Season.Status.ACTIVE);
        when(store.findById(LIST_SCOPE, SEASON_ID)).thenReturn(Optional.of(planned));
        when(store.liveParentsAvailable(any(), any(), any(), any(), any())).thenReturn(true);
        when(store.transition(
                FARM_SCOPE, SEASON_ID, 0, Season.Status.PLANNED, Season.Status.ACTIVE,
                LocalDate.parse("2027-01-02"))).thenReturn(Optional.of(active));

        assertThat(service.transition(
                SEASON_ID,
                new SeasonCommands.Transition(
                        Season.Status.ACTIVE, LocalDate.parse("2027-01-02"), 0, AUDIT)))
                .isEqualTo(active);

        verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }
}
