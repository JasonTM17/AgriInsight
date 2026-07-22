package com.agriinsight.backend.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityApplicationContractsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("ACTIVITY_CHANGE"), Optional.of("request-activity-01"));

    @Test
    void listQueryNormalizesBoundedAllowlistedFilters() {
        ActivityQuery query = new ActivityQuery(
                25, 50, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(ActivityType.IRRIGATION), Optional.of(ActivityStatus.PLANNED),
                Optional.of("  north rows  "));

        assertThat(query.search()).contains("north rows");
        assertThat(query.activityType()).contains(ActivityType.IRRIGATION);
        assertThat(query.status()).contains(ActivityStatus.PLANNED);
        assertThatThrownBy(() -> new ActivityQuery(
                101, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void patchSupportsExplicitDescriptionClearAndRejectsEmptyCommands() {
        ActivityCommands.Update clearDescription = new ActivityCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(Optional.empty()),
                Optional.empty(), Optional.empty(), 2, AUDIT);

        assertThat(clearDescription.description()).contains(Optional.empty());
        assertThatThrownBy(() -> new ActivityCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void transitionRequiresNonnegativeVersion() {
        Instant effectiveAt = Instant.parse("2027-03-01T02:00:00Z");

        assertThatThrownBy(() -> new ActivityCommands.Transition(
                ActivityStatus.STARTED, effectiveAt, -1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
        assertThat(new ActivityCommands.Transition(
                ActivityStatus.CANCELLED, effectiveAt, 0, AUDIT).targetStatus())
                .isEqualTo(ActivityStatus.CANCELLED);
    }
}
