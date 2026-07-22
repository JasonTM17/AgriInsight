package com.agriinsight.backend.cost.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatingCostEntryTest {

    private static final UUID ENTRY_ID = UUID.fromString(
            "62000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000005");

    @Test
    void categoryCatalogIsAnExplicitVersionedContract() {
        assertThat(EnumSet.allOf(CostCategory.class)).containsExactly(
                CostCategory.LABOR,
                CostCategory.MATERIAL,
                CostCategory.MACHINERY,
                CostCategory.TRANSPORT,
                CostCategory.UTILITY,
                CostCategory.OTHER);
    }

    @Test
    void targetRequiresExactlyOneCanonicalDomainIdentifier() {
        UUID farmId = UUID.randomUUID();
        assertThat(CostTarget.tenant().id()).isEmpty();
        assertThat(CostTarget.domain(CostTarget.Type.FARM, farmId).id())
                .contains(farmId);
        assertThatThrownBy(() -> new CostTarget(
                CostTarget.Type.TENANT, Optional.of(farmId)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CostTarget(
                CostTarget.Type.FIELD, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountUsesPositiveVndPrecisionWithoutImplicitRounding() {
        assertThat(OperatingCostEntry.positiveVnd(new BigDecimal("1000.00")))
                .isEqualByComparingTo("1000");
        assertThatThrownBy(() -> OperatingCostEntry.positiveVnd(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OperatingCostEntry.positiveVnd(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
    }

    @Test
    void reversalShapeIsOneWayAndUsesInstantForUtcTime() {
        Instant occurredAt = Instant.parse("2027-09-01T02:00:00Z");
        OperatingCostEntry posting = entry(
                CostEntryKind.POSTING, Optional.empty(), occurredAt);
        assertThat(posting.occurredAt()).isEqualTo(occurredAt);
        assertThatThrownBy(() -> entry(
                CostEntryKind.REVERSAL, Optional.empty(), occurredAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(
                CostEntryKind.POSTING, Optional.of(UUID.randomUUID()), occurredAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OperatingCostEntry entry(
            CostEntryKind kind, Optional<UUID> reversalOf, Instant occurredAt) {
        return new OperatingCostEntry(
                ENTRY_ID,
                TENANT_ID,
                CostTarget.tenant(),
                CostCategory.LABOR,
                new BigDecimal("1000.00"),
                kind,
                occurredAt,
                Optional.of("Seasonal workers"),
                Optional.of("PAYROLL-2027-09"),
                reversalOf,
                "a".repeat(64),
                PROFILE_ID);
    }
}
