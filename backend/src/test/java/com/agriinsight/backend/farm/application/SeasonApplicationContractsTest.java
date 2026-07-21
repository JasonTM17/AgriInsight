package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.domain.Season;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeasonApplicationContractsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("SEASON_CHANGE"), Optional.of("request-03"));

    @Test
    void listQueryNormalizesOnlyBoundedAllowlistedFilters() {
        SeasonQuery query = new SeasonQuery(
                25, 50, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Season.Status.ACTIVE), Optional.of("  arabica  "));

        assertThat(query.search()).contains("arabica");
        assertThat(query.status()).contains(Season.Status.ACTIVE);
        assertThat(query.limit()).isEqualTo(25);
        assertThat(query.offset()).isEqualTo(50);
        assertThatThrownBy(() -> new SeasonQuery(
                101, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patchSupportsExplicitNullableClearsButRejectsEmptyCommands() {
        SeasonCommands.Update clearBudget = new SeasonCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(Optional.empty()), 2, AUDIT);

        assertThat(clearBudget.budgetVnd()).contains(Optional.empty());
        assertThatThrownBy(() -> new SeasonCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void transitionRequiresEffectiveDateAndNonnegativeVersion() {
        assertThatThrownBy(() -> new SeasonCommands.Transition(
                Season.Status.ACTIVE, LocalDate.now(), -1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
        assertThat(new SeasonCommands.Transition(
                Season.Status.CANCELLED, LocalDate.parse("2027-04-01"), 0, AUDIT).targetStatus())
                .isEqualTo(Season.Status.CANCELLED);
        assertThat(Season.positiveArea(new BigDecimal("1.0000"))).isEqualByComparingTo("1");
    }
}
