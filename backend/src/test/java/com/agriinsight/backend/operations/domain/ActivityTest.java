package com.agriinsight.backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.operations.application.ActivityRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final UUID SEASON_ID = UUID.fromString("35000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVITY_ID = UUID.fromString("36000000-0000-0000-0000-000000000001");
    private static final Instant PLANNED_START = Instant.parse("2027-03-01T01:00:00Z");
    private static final Instant DUE_AT = Instant.parse("2027-03-01T05:00:00Z");

    @Test
    void canonicalizesTaskMetadata() {
        Activity activity = activity(" irrigation-01 ", " Morning irrigation ", Optional.of(" North rows "));

        assertThat(activity.code()).isEqualTo("IRRIGATION-01");
        assertThat(activity.title()).isEqualTo("Morning irrigation");
        assertThat(activity.description()).contains("North rows");
        assertThat(activity.activityType()).isEqualTo(ActivityType.IRRIGATION);
    }

    @Test
    void rejectsInvalidCodesTextAndSchedule() {
        assertThatThrownBy(() -> activity("bad code", "Irrigation", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        assertThatThrownBy(() -> activity("IRRIGATION-01", " ", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> new Activity(
                ACTIVITY_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, ActivityType.IRRIGATION,
                "IRRIGATION-01", "Irrigation", Optional.empty(), DUE_AT, PLANNED_START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dueAt");
    }

    @Test
    void statusMachineIsOneWayAndRecordTimesMatchStatus() {
        assertThat(ActivityStatus.PLANNED.canTransitionTo(ActivityStatus.STARTED)).isTrue();
        assertThat(ActivityStatus.PLANNED.canTransitionTo(ActivityStatus.CANCELLED)).isTrue();
        assertThat(ActivityStatus.STARTED.canTransitionTo(ActivityStatus.COMPLETED)).isTrue();
        assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.STARTED)).isFalse();

        assertThatThrownBy(() -> record(
                Optional.empty(), Optional.empty(), Optional.empty(), ActivityStatus.STARTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status times");
        assertThat(record(
                Optional.of(PLANNED_START), Optional.of(DUE_AT), Optional.empty(),
                ActivityStatus.COMPLETED).status()).isEqualTo(ActivityStatus.COMPLETED);
    }

    private Activity activity(String code, String title, Optional<String> description) {
        return new Activity(
                ACTIVITY_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, ActivityType.IRRIGATION,
                code, title, description, PLANNED_START, DUE_AT);
    }

    private ActivityRecord record(
            Optional<Instant> startedAt,
            Optional<Instant> completedAt,
            Optional<Instant> cancelledAt,
            ActivityStatus status) {
        return new ActivityRecord(
                ACTIVITY_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, ActivityType.IRRIGATION,
                "IRRIGATION-01", "Irrigation", Optional.empty(), PLANNED_START, DUE_AT,
                startedAt, completedAt, cancelledAt, status, 0);
    }
}
