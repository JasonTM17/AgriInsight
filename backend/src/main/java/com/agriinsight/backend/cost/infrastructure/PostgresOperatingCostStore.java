package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.cost.application.CostCorrectionRecord;
import com.agriinsight.backend.cost.application.OperatingCostRecord;
import com.agriinsight.backend.cost.application.OperatingCostStore;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import java.sql.Types;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresOperatingCostStore implements OperatingCostStore {

    private final JdbcTemplate jdbcTemplate;
    private final OperatingCostTargetQueries targets;

    public PostgresOperatingCostStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "jdbcTemplate is required");
        this.targets = new OperatingCostTargetQueries(jdbcTemplate);
    }

    @Override
    public boolean targetAvailable(ScopeContext scope, CostTarget target) {
        return targets.available(scope, target);
    }

    @Override
    public Optional<OperatingCostRecord> findById(
            ScopeContext scope, UUID entryId) {
        ScopeContext required = OperatingCostStoreScope.requireTenant(scope);
        return find(required.tenantId(), entryId);
    }

    @Override
    public Optional<OperatingCostRecord> append(
            ScopeContext scope, OperatingCostEntry entry) {
        ScopeContext required = OperatingCostStoreScope.requireTenant(scope);
        OperatingCostEntry value = requireBinding(required, entry);
        if (value.kind() != CostEntryKind.POSTING || !targets.available(required, value.target())) {
            return Optional.empty();
        }
        return insert(value);
    }

    @Override
    public Optional<CostCorrectionRecord> appendCorrection(
            ScopeContext scope,
            UUID originalEntryId,
            OperatingCostEntry reversal,
            OperatingCostEntry replacement) {
        ScopeContext required = OperatingCostStoreScope.requireTenant(scope);
        UUID originalId = Objects.requireNonNull(
                originalEntryId, "originalEntryId is required");
        OperatingCostEntry reversalEntry = requireBinding(required, reversal);
        OperatingCostEntry replacementEntry = requireBinding(required, replacement);
        lockCorrection(required.tenantId(), originalId);
        Optional<OperatingCostRecord> original = find(required.tenantId(), originalId);
        if (original.isEmpty()
                || !validCorrection(original.orElseThrow(), reversalEntry, replacementEntry)
                || correctionExists(required.tenantId(), originalId)
                || !targets.available(required, replacementEntry.target())) {
            return Optional.empty();
        }
        OperatingCostRecord savedReversal = insert(reversalEntry).orElseThrow();
        OperatingCostRecord savedReplacement = insert(replacementEntry).orElseThrow();
        return Optional.of(new CostCorrectionRecord(savedReversal, savedReplacement));
    }

    @Override
    public Optional<CostCorrectionRecord> findCorrectionByReplacementId(
            ScopeContext scope, UUID replacementEntryId) {
        ScopeContext required = OperatingCostStoreScope.requireTenant(scope);
        Optional<OperatingCostRecord> replacement = find(
                required.tenantId(), replacementEntryId);
        if (replacement.isEmpty()
                || replacement.orElseThrow().kind() != CostEntryKind.POSTING) {
            return Optional.empty();
        }
        List<OperatingCostRecord> reversals = jdbcTemplate.query("""
                SELECT %s FROM operating_cost_entries AS entry
                 WHERE entry.tenant_id = ? AND entry.command_reference = ?
                   AND entry.entry_kind = 'REVERSAL'
                """.formatted(OperatingCostRowMapping.COLUMNS),
                OperatingCostRowMapping.MAPPER,
                required.tenantId(), replacement.orElseThrow().commandReference());
        return OperatingCostRowMapping.exactlyOneOrEmpty(reversals)
                .map(reversal -> new CostCorrectionRecord(
                        reversal, replacement.orElseThrow()));
    }

    private Optional<OperatingCostRecord> find(UUID tenantId, UUID entryId) {
        return OperatingCostRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                SELECT %s FROM operating_cost_entries AS entry
                 WHERE entry.tenant_id = ? AND entry.id = ?
                """.formatted(OperatingCostRowMapping.COLUMNS),
                OperatingCostRowMapping.MAPPER,
                tenantId, Objects.requireNonNull(entryId, "entryId is required")));
    }

    private Optional<OperatingCostRecord> insert(OperatingCostEntry entry) {
        return OperatingCostRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO operating_cost_entries AS entry (
                    id, tenant_id, target_type, farm_id, field_id, season_id, activity_id,
                    category_code, amount_vnd, entry_kind, occurred_at, description,
                    source_reference, reversal_of, command_reference, recorded_by_profile_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(OperatingCostRowMapping.COLUMNS),
                OperatingCostRowMapping.MAPPER,
                entry.id(), entry.tenantId(), entry.target().type().name(),
                targetId(entry, CostTarget.Type.FARM),
                targetId(entry, CostTarget.Type.FIELD),
                targetId(entry, CostTarget.Type.SEASON),
                targetId(entry, CostTarget.Type.ACTIVITY),
                entry.category().name(), entry.amountVnd(), entry.kind().name(),
                Timestamp.from(entry.occurredAt()),
                text(entry.description()), text(entry.sourceReference()),
                uuid(entry.reversalOf()), entry.commandReference(), entry.recordedByProfileId()));
    }

    private boolean validCorrection(
            OperatingCostRecord original,
            OperatingCostEntry reversal,
            OperatingCostEntry replacement) {
        return original.kind() == CostEntryKind.POSTING
                && reversal.kind() == CostEntryKind.REVERSAL
                && reversal.reversalOf().filter(original.id()::equals).isPresent()
                && reversal.target().equals(original.target())
                && reversal.category() == original.category()
                && reversal.amountVnd().compareTo(original.amountVnd()) == 0
                && reversal.occurredAt().equals(original.occurredAt())
                && replacement.kind() == CostEntryKind.POSTING
                && reversal.commandReference().equals(replacement.commandReference());
    }

    private boolean correctionExists(UUID tenantId, UUID originalId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM operating_cost_entries
                 WHERE tenant_id = ? AND reversal_of = ?
                """, Long.class, tenantId, originalId);
        return Objects.requireNonNull(count, "Correction count is required") > 0;
    }

    private void lockCorrection(UUID tenantId, UUID originalId) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))",
                Object.class, tenantId.toString(), originalId.toString());
    }

    private OperatingCostEntry requireBinding(
            ScopeContext scope, OperatingCostEntry entry) {
        OperatingCostEntry value = Objects.requireNonNull(entry, "entry is required");
        if (!scope.tenantId().equals(value.tenantId())
                || !scope.profileId().equals(value.recordedByProfileId())) {
            throw new IllegalArgumentException(
                    "Operating cost entry cannot switch tenant or recorder");
        }
        return value;
    }

    private Object targetId(OperatingCostEntry entry, CostTarget.Type type) {
        return entry.target().type() == type
                ? entry.target().id().orElseThrow()
                : new SqlParameterValue(Types.OTHER, null);
    }

    private Object text(Optional<String> value) {
        return value.<Object>map(item -> item)
                .orElseGet(() -> new SqlParameterValue(Types.VARCHAR, null));
    }

    private Object uuid(Optional<UUID> value) {
        return value.<Object>map(item -> item)
                .orElseGet(() -> new SqlParameterValue(Types.OTHER, null));
    }
}
