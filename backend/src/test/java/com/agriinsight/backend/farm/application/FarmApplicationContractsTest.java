package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FarmApplicationContractsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("MASTER_DATA_CHANGE"), Optional.of("request-01"));

    @Test
    void listQueryNormalizesOnlyAllowlistedFilters() {
        FarmQuery query = new FarmQuery(25, 50, Optional.of(true), Optional.of("  north  "));

        assertThat(query.search()).contains("north");
        assertThat(query.active()).contains(true);
        assertThat(query.limit()).isEqualTo(25);
        assertThat(query.offset()).isEqualTo(50);
    }

    @Test
    void listAndPatchContractsRejectUnboundedOrEmptyRequests() {
        assertThatThrownBy(() -> new FarmQuery(101, 0, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FarmCommands.Update(
                Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }
}
