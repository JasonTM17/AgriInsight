package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityQuery;
import com.agriinsight.backend.operations.application.ActivityService;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/activities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityReadController {

    private final ActivityService activities;

    public ActivityReadController(ActivityService activities) {
        this.activities = activities;
    }

    @GetMapping
    ResponseEntity<ActivityPageResponse> list(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID fieldId,
            @RequestParam(required = false) UUID seasonId,
            @RequestParam(required = false) ActivityType activityType,
            @RequestParam(required = false) ActivityStatus status,
            @RequestParam(required = false) String search) {
        var page = activities.list(new ActivityQuery(
                limit, offset, Optional.ofNullable(farmId), Optional.ofNullable(fieldId),
                Optional.ofNullable(seasonId), Optional.ofNullable(activityType),
                Optional.ofNullable(status), Optional.ofNullable(search)));
        return ResponseEntity.ok(ActivityPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<ActivityResponse> get(@PathVariable("id") UUID activityId) {
        ActivityResponse response = ActivityResponse.from(activities.get(activityId));
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }
}
