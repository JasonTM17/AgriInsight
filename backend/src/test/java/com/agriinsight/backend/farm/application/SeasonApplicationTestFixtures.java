package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Season;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

final class SeasonApplicationTestFixtures {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    static final UUID SEASON_ID = UUID.fromString("35000000-0000-0000-0000-000000000001");
    static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    static final ScopeContext LIST_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
    static final ScopeContext FARM_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));
    static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("SEASON_CHANGE"), Optional.of("request-04"));

    private SeasonApplicationTestFixtures() {
    }

    static SeasonRecord season(long version, Season.Status status) {
        Optional<LocalDate> started = status == Season.Status.PLANNED || status == Season.Status.CANCELLED
                ? Optional.empty() : Optional.of(LocalDate.parse("2027-01-02"));
        Optional<LocalDate> ended = status.terminal()
                ? Optional.of(LocalDate.parse("2027-11-30")) : Optional.empty();
        return new SeasonRecord(
                SEASON_ID, TENANT_ID, FARM_ID, FIELD_ID, CROP_ID,
                "SEASON-A", "Season A", Optional.of("Arabica"),
                LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"),
                started, ended, new BigDecimal("10"), Optional.of(new BigDecimal("1000000")),
                status, version);
    }

    static SeasonCommands.Create createCommand() {
        return new SeasonCommands.Create(
                FARM_ID, FIELD_ID, CROP_ID, "SEASON-A", "Season A", Optional.of("Arabica"),
                LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"),
                new BigDecimal("10"), Optional.of(new BigDecimal("1000000")), AUDIT);
    }

    static SeasonCommands.Update updateCommand(long version) {
        return new SeasonCommands.Update(
                Optional.empty(), Optional.of("Updated Season"), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), version, AUDIT);
    }

    private record TestPrincipal() implements TenantPrincipal {

        @Override
        public UUID profileId() {
            return PROFILE_ID;
        }

        @Override
        public UUID tenantId() {
            return TENANT_ID;
        }

        @Override
        public String getName() {
            return PROFILE_ID.toString();
        }
    }
}
