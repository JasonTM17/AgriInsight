package com.agriinsight.backend.cost.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperatingCostQueryTest {

    @Test
    void listAndSummaryRequireAPositiveRangeNoLongerThan366Days() {
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant validTo = Instant.parse("2028-01-02T00:00:00Z");
        query(from, validTo);
        summary(from, validTo);

        assertThatThrownBy(() -> query(from, from))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after");
        assertThatThrownBy(() -> summary(
                from, Instant.parse("2028-01-03T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    @Test
    void listBoundsPageSizeAndOffset() {
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-02-01T00:00:00Z");
        assertThatThrownBy(() -> new OperatingCostQuery(
                101, 0, from, to, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new OperatingCostQuery(
                25, 10_001, from, to, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }

    private OperatingCostQuery query(Instant from, Instant to) {
        return new OperatingCostQuery(
                25, 0, from, to, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private CostSummaryQuery summary(Instant from, Instant to) {
        return new CostSummaryQuery(
                from, to, CostSummaryGroup.MONTH,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
