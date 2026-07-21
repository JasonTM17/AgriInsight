package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Farm;
import java.util.Optional;
import java.util.UUID;

public interface FarmStore {

    FarmPage findAll(ScopeContext scope, FarmQuery query);

    Optional<FarmRecord> findById(ScopeContext scope, UUID farmId);

    FarmRecord create(ScopeContext scope, Farm farm);

    Optional<FarmRecord> update(
            ScopeContext scope,
            UUID farmId,
            long expectedVersion,
            Optional<String> code,
            Optional<String> displayName);

    Optional<FarmRecord> updateActive(
            ScopeContext scope,
            UUID farmId,
            long expectedVersion,
            boolean active);

    boolean hasDeactivationBlockers(ScopeContext scope, UUID farmId);
}
