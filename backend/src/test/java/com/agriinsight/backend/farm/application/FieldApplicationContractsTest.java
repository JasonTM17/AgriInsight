package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FieldApplicationContractsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("FIELD_CHANGE"), Optional.of("request-05"));

    @Test
    void updateDistinguishesOmittedValuesFromExplicitClears() {
        FieldCommands.Update update = new FieldCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Optional.empty()), Optional.of(Optional.empty()),
                Optional.of(Optional.empty()), Optional.of(Optional.empty()), 2, AUDIT);

        assertThat(update.responsibleEmployeeId()).contains(Optional.empty());
        assertThat(update.coordinates()).contains(Optional.empty());
        assertThat(update.soilType()).contains(Optional.empty());
        assertThat(update.irrigationType()).contains(Optional.empty());
    }

    @Test
    void rejectsEmptyUpdatesAndUnboundedQueries() {
        assertThatThrownBy(() -> new FieldCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> new FieldQuery(
                101, 0, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void canonicalizesPatchValuesBeforePersistence() {
        FieldCommands.Update update = new FieldCommands.Update(
                Optional.of(" field-b "), Optional.of(" South Field "),
                Optional.of(new BigDecimal("8.0000")), Optional.empty(), Optional.empty(),
                Optional.of(Optional.of(" Clay ")), Optional.empty(), 1, AUDIT);

        assertThat(update.code()).contains("FIELD-B");
        assertThat(update.areaHectares()).hasValueSatisfying(
                area -> assertThat(area).isEqualByComparingTo("8"));
        assertThat(update.soilType()).contains(Optional.of("Clay"));
    }
}
