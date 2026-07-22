package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.OperatingCostQuery;
import com.agriinsight.backend.cost.application.OperatingCostQueryService;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
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
@RequestMapping(ApiVersion.PREFIX + "/cost-entries")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class OperatingCostReadController {

    private final OperatingCostQueryService costs;

    public OperatingCostReadController(OperatingCostQueryService costs) {
        this.costs = costs;
    }

    @Operation(
            summary = "List operating cost ledger entries",
            description = "Returns at most 100 entries within a required UTC period of 366 days or less.")
    @GetMapping
    OperatingCostPageResponse list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam Instant occurredFrom,
            @RequestParam Instant occurredTo,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID fieldId,
            @RequestParam(required = false) UUID seasonId,
            @RequestParam(required = false) UUID activityId,
            @RequestParam(required = false) CostCategory category,
            @RequestParam(required = false) CostTarget.Type targetType,
            @RequestParam(required = false) CostEntryKind entryKind) {
        return OperatingCostPageResponse.from(costs.list(new OperatingCostQuery(
                limit, offset, occurredFrom, occurredTo,
                Optional.ofNullable(farmId), Optional.ofNullable(fieldId),
                Optional.ofNullable(seasonId), Optional.ofNullable(activityId),
                Optional.ofNullable(category), Optional.ofNullable(targetType),
                Optional.ofNullable(entryKind))));
    }

    @Operation(summary = "Get an operating cost ledger entry")
    @GetMapping("/{id}")
    ResponseEntity<OperatingCostResponse> get(@PathVariable UUID id) {
        OperatingCostResponse response = OperatingCostResponse.from(costs.get(id));
        return ResponseEntity.ok()
                .headers(ApiCommandResponses.headers(response.version()))
                .body(response);
    }
}
