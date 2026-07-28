package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeSummaryService;
import com.agriinsight.backend.shared.api.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/realtime/summary")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class RealtimeSummaryController {

    private final RealtimeSummaryService summaries;

    public RealtimeSummaryController(RealtimeSummaryService summaries) {
        this.summaries = Objects.requireNonNull(summaries, "summaries is required");
    }

    @Operation(
            summary = "Get the tenant realtime operational summary",
            description = "Returns at most 100 payload-free metric groups with non-negative freshness.")
    @GetMapping
    RealtimeSummaryResponse summarize() {
        return RealtimeSummaryResponse.from(summaries.summarize());
    }
}
