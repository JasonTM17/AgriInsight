package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Field;
import java.util.Optional;
import java.util.UUID;

public interface FieldStore {

    FieldPage findAll(ScopeContext scope, FieldQuery query);

    Optional<FieldRecord> findById(ScopeContext scope, UUID fieldId);

    boolean liveParentsAvailable(
            ScopeContext scope,
            UUID farmId,
            Optional<UUID> responsibleEmployeeId);

    FieldRecord create(ScopeContext scope, Field field);

    Optional<FieldRecord> update(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            FieldCommands.Update command);

    Optional<FieldRecord> updateActive(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            boolean active);

    boolean hasDeactivationBlockers(ScopeContext scope, UUID fieldId);
}
