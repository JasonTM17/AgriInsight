package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityLogReadService;
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
@RequestMapping(ApiVersion.PREFIX + "/activities/{id}/logs")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class ActivityLogReadController {

    private final ActivityLogReadService logs;

    public ActivityLogReadController(ActivityLogReadService logs) {
        this.logs = logs;
    }

    @GetMapping
    ResponseEntity<ActivityLogPageResponse> list(
            @PathVariable("id") UUID activityId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset) {
        return ResponseEntity.ok(ActivityLogPageResponse.from(
                logs.list(activityId, new ActivityReadPageQuery(limit, offset))));
    }

    @GetMapping("/{logId}/history")
    ResponseEntity<ActivityLogPageResponse> history(
            @PathVariable("id") UUID activityId,
            @PathVariable UUID logId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset) {
        return ResponseEntity.ok(ActivityLogPageResponse.from(
                logs.history(activityId, logId, new ActivityReadPageQuery(limit, offset))));
    }
}
