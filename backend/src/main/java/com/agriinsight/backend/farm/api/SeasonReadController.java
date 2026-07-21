package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.SeasonQuery;
import com.agriinsight.backend.farm.application.SeasonService;
import com.agriinsight.backend.farm.domain.Season;
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
@RequestMapping(ApiVersion.PREFIX + "/seasons")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SeasonReadController {

    private final SeasonService seasons;

    public SeasonReadController(SeasonService seasons) {
        this.seasons = seasons;
    }

    @GetMapping
    ResponseEntity<SeasonPageResponse> list(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID fieldId,
            @RequestParam(required = false) UUID cropId,
            @RequestParam(required = false) Season.Status status,
            @RequestParam(required = false) String search) {
        var page = seasons.list(new SeasonQuery(
                limit, offset,
                Optional.ofNullable(farmId),
                Optional.ofNullable(fieldId),
                Optional.ofNullable(cropId),
                Optional.ofNullable(status),
                Optional.ofNullable(search)));
        return ResponseEntity.ok(SeasonPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<SeasonResponse> get(@PathVariable("id") UUID seasonId) {
        SeasonResponse response = SeasonResponse.from(seasons.get(seasonId));
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }
}
