package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Season;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SeasonStore {

    SeasonPage findAll(ScopeContext scope, SeasonQuery query);

    Optional<SeasonRecord> findById(ScopeContext scope, UUID seasonId);

    boolean farmVisible(ScopeContext scope, UUID farmId);

    boolean liveParentsAvailable(
            ScopeContext scope,
            UUID farmId,
            UUID fieldId,
            UUID cropId,
            BigDecimal plantedAreaHectares);

    Optional<SeasonRecord> create(ScopeContext scope, Season season);

    Optional<SeasonRecord> update(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            SeasonCommands.Update command);

    Optional<SeasonRecord> transition(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            Season.Status sourceStatus,
            Season.Status targetStatus,
            LocalDate effectiveDate);
}
