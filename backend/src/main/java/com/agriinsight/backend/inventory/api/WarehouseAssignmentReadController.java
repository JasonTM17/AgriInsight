package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.WarehouseAssignmentQuery;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentService;
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
@RequestMapping(ApiVersion.PREFIX + "/warehouse-assignments")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class WarehouseAssignmentReadController {

    private final WarehouseAssignmentService assignments;

    public WarehouseAssignmentReadController(WarehouseAssignmentService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    ResponseEntity<WarehouseAssignmentPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID userProfileId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(WarehouseAssignmentPageResponse.from(assignments.list(
                new WarehouseAssignmentQuery(
                        limit,
                        offset,
                        Optional.ofNullable(userProfileId),
                        Optional.ofNullable(warehouseId),
                        Optional.ofNullable(active)))));
    }
}
