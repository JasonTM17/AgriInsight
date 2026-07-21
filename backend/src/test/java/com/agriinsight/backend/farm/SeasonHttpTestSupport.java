package com.agriinsight.backend.farm;

import com.agriinsight.backend.farm.application.SeasonRecord;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

final class SeasonHttpTestSupport {

    static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    static final UUID SEASON_ID = UUID.fromString("35000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000005");

    private SeasonHttpTestSupport() {
    }

    static SeasonRecord season(long version, Season.Status status) {
        Optional<LocalDate> started = status == Season.Status.PLANNED || status == Season.Status.CANCELLED
                ? Optional.empty() : Optional.of(LocalDate.parse("2027-01-02"));
        Optional<LocalDate> ended = status.terminal()
                ? Optional.of(LocalDate.parse("2027-11-30")) : Optional.empty();
        return new SeasonRecord(
                SEASON_ID,
                FarmHttpTestSupport.TENANT_ID,
                FarmHttpTestSupport.FARM_ID,
                FIELD_ID,
                CROP_ID,
                "SEASON-A",
                "Season A",
                Optional.of("Arabica"),
                LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-12-31"),
                started,
                ended,
                new BigDecimal("10"),
                Optional.of(new BigDecimal("1000000")),
                status,
                version);
    }

    static CommandExecutionResult.Completed<SeasonRecord> completed(
            int status,
            SeasonRecord season) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                false,
                status,
                new CommandTarget("SEASON", season.id(), season.version()),
                Optional.of(season));
    }
}
