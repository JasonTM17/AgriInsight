package com.agriinsight.backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityLogTest {

    private static final UUID ID = UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("61000000-0000-0000-0000-000000000002");
    private static final UUID ACTIVITY_ID = UUID.fromString("61000000-0000-0000-0000-000000000003");
    private static final UUID EMPLOYEE_ID = UUID.fromString("61000000-0000-0000-0000-000000000004");
    private static final UUID PROFILE_ID = UUID.fromString("61000000-0000-0000-0000-000000000005");

    @Test
    void canonicalizesBoundedEvidencePayload() {
        ActivityLog log = log(
                Optional.of(" Harvested row A "), Optional.of(new BigDecimal("100.0000")),
                Optional.of(ActivityLogUnit.KG), Optional.of("https://evidence.example/log-1"),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(log.notes()).contains("Harvested row A");
        assertThat(log.quantity()).contains(new BigDecimal("1E+2"));
        assertThat(log.evidenceUri()).contains("https://evidence.example/log-1");
    }

    @Test
    void rejectsUnsafeUriAndIncompleteQuantityUnitPair() {
        assertThatThrownBy(() -> log(
                Optional.of("Evidence"), Optional.empty(), Optional.empty(),
                Optional.of("file:///etc/passwd"), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceUri");
        assertThatThrownBy(() -> log(
                Optional.empty(), Optional.of(BigDecimal.ONE), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity and unit");
    }

    @Test
    void voidCorrectionCannotCarryQuantitativeEvidence() {
        assertThatThrownBy(() -> log(
                Optional.of("Void duplicate"), Optional.of(BigDecimal.ONE),
                Optional.of(ActivityLogUnit.KG), Optional.empty(), Optional.of(UUID.randomUUID()),
                Optional.of(ActivityLogCorrectionKind.VOID), Optional.of("Duplicate")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("void correction");
    }

    private ActivityLog log(
            Optional<String> notes,
            Optional<BigDecimal> quantity,
            Optional<ActivityLogUnit> unit,
            Optional<String> evidenceUri,
            Optional<UUID> correctsLogId,
            Optional<ActivityLogCorrectionKind> correctionKind,
            Optional<String> correctionReason) {
        return new ActivityLog(
                ID, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID, PROFILE_ID,
                Instant.parse("2027-01-01T01:00:00Z"), notes, quantity, unit, evidenceUri,
                correctsLogId, correctionKind, correctionReason);
    }
}
