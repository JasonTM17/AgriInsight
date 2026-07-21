package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FarmQuery;
import com.agriinsight.backend.farm.application.FarmService;
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
@RequestMapping(ApiVersion.PREFIX + "/farms")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FarmReadController {

    private final FarmService farms;

    public FarmReadController(FarmService farms) {
        this.farms = farms;
    }

    @GetMapping
    ResponseEntity<FarmPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = farms.list(new FarmQuery(
                limit,
                offset,
                Optional.ofNullable(active),
                Optional.ofNullable(search)));
        return ResponseEntity.ok(FarmPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<FarmResponse> get(@PathVariable("id") UUID farmId) {
        FarmResponse response = FarmResponse.from(farms.get(farmId));
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
