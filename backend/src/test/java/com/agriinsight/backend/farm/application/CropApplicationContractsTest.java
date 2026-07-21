package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CropApplicationContractsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("CROP_CHANGE"), Optional.of("request-06"));

    @Test
    void updateDistinguishesOmittedScientificNameFromExplicitClear() {
        CropCommands.Update update = new CropCommands.Update(
                Optional.empty(), Optional.empty(), Optional.of(Optional.empty()), 2, AUDIT);

        assertThat(update.scientificName()).contains(Optional.empty());
    }

    @Test
    void rejectsEmptyUpdatesAndUnboundedQueries() {
        assertThatThrownBy(() -> new CropCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> new CropQuery(
                101, 0, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
