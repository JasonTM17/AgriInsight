package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityAssignmentReadService;
import com.agriinsight.backend.operations.application.ActivityReadPageQuery;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/activities/{id}/assignments")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityAssignmentReadController {

    private final ActivityAssignmentReadService assignments;

    public ActivityAssignmentReadController(ActivityAssignmentReadService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    ResponseEntity<ActivityAssignmentPageResponse> list(
            @PathVariable("id") UUID activityId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset) {
        return ResponseEntity.ok(ActivityAssignmentPageResponse.from(
                assignments.list(activityId, new ActivityReadPageQuery(limit, offset))));
    }
}
