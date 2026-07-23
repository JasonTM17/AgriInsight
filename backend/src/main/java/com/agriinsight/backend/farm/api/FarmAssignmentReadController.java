package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FarmAssignmentQuery;
import com.agriinsight.backend.farm.application.FarmAssignmentService;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/farm-assignments")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FarmAssignmentReadController {

    private final FarmAssignmentService assignments;

    public FarmAssignmentReadController(FarmAssignmentService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    ResponseEntity<FarmAssignmentPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID userProfileId,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(FarmAssignmentPageResponse.from(assignments.list(
                new FarmAssignmentQuery(
                        limit,
                        offset,
                        Optional.ofNullable(userProfileId),
                        Optional.ofNullable(farmId),
                        Optional.ofNullable(active)))));
    }
}
