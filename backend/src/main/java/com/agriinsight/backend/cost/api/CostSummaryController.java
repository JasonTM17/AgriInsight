package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.CostSummaryGroup;
import com.agriinsight.backend.cost.application.CostSummaryQuery;
import com.agriinsight.backend.cost.application.OperatingCostQueryService;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.shared.api.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/cost-summaries")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CostSummaryController {

    private final OperatingCostQueryService costs;

    public CostSummaryController(OperatingCostQueryService costs) {
        this.costs = costs;
    }

    @Operation(
            summary = "Summarize the operating cost ledger",
            description = "Groups only the OPERATING_COST lens. Procurement spend and inventory "
                    + "value are never merged into these totals.")
    @GetMapping
    CostSummaryResponse summarize(
            @RequestParam Instant occurredFrom,
            @RequestParam Instant occurredTo,
            @RequestParam(defaultValue = "MONTH") CostSummaryGroup groupBy,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID seasonId,
            @RequestParam(required = false) CostCategory category) {
        return CostSummaryResponse.from(costs.summarize(new CostSummaryQuery(
                occurredFrom, occurredTo, groupBy,
                Optional.ofNullable(farmId), Optional.ofNullable(seasonId),
                Optional.ofNullable(category))));
    }
}
