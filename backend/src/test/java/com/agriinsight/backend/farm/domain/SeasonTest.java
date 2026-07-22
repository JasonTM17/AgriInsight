package com.agriinsight.backend.farm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.farm.application.SeasonRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeasonTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    private static final UUID SEASON_ID = UUID.fromString("35000000-0000-0000-0000-000000000001");

    @Test
    void canonicalizesMasterDataAndExactDecimals() {
        Season season = season(" harvest_27 ", new BigDecimal("10.0000"), Optional.of(new BigDecimal("1200.00")));

        assertThat(season.code()).isEqualTo("HARVEST_27");
        assertThat(season.displayName()).isEqualTo("Harvest Season");
        assertThat(season.varietyName()).contains("Arabica");
        assertThat(season.plantedAreaHectares()).isEqualByComparingTo("10");
        assertThat(season.budgetVnd())
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("1200"));
    }

    @Test
    void rejectsInvalidDateAndDecimalContracts() {
        assertThatThrownBy(() -> new Season(
                SEASON_ID, TENANT_ID, FARM_ID, FIELD_ID, CROP_ID, "S-1", "Season",
                Optional.empty(), LocalDate.parse("2027-12-31"), LocalDate.parse("2027-01-01"),
                BigDecimal.ONE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plannedEndDate");
        assertThatThrownBy(() -> season("S-1", BigDecimal.ZERO, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> season("S-1", BigDecimal.ONE, Optional.of(new BigDecimal("-0.01"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("budgetVnd");
        assertThatThrownBy(() -> new Season(
                SEASON_ID, TENANT_ID, FARM_ID, FIELD_ID, CROP_ID, "S-1", "Season",
                Optional.of("V".repeat(161)), LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-12-31"), BigDecimal.ONE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("varietyName");
    }

    @Test
    void stateMachineIsOneWayAndStatusDatesMustMatch() {
        assertThat(Season.Status.PLANNED.canTransitionTo(Season.Status.ACTIVE)).isTrue();
        assertThat(Season.Status.PLANNED.canTransitionTo(Season.Status.CANCELLED)).isTrue();
        assertThat(Season.Status.ACTIVE.canTransitionTo(Season.Status.COMPLETED)).isTrue();
        assertThat(Season.Status.COMPLETED.canTransitionTo(Season.Status.ACTIVE)).isFalse();

        assertThatThrownBy(() -> record(
                Optional.empty(), Optional.empty(), Season.Status.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status dates");
        assertThat(record(
                Optional.of(LocalDate.parse("2027-01-02")),
                Optional.of(LocalDate.parse("2027-11-30")),
                Season.Status.COMPLETED).status()).isEqualTo(Season.Status.COMPLETED);
    }

    private Season season(String code, BigDecimal area, Optional<BigDecimal> budget) {
        return new Season(
                SEASON_ID, TENANT_ID, FARM_ID, FIELD_ID, CROP_ID, code, " Harvest Season ",
                Optional.of(" Arabica "), LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-12-31"), area, budget);
    }

    private SeasonRecord record(
            Optional<LocalDate> startedOn,
            Optional<LocalDate> endedOn,
            Season.Status status) {
        return new SeasonRecord(
                SEASON_ID, TENANT_ID, FARM_ID, FIELD_ID, CROP_ID, "S-1", "Season",
                Optional.empty(), LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"),
                startedOn, endedOn, BigDecimal.ONE, Optional.empty(), status, 0);
    }
}
