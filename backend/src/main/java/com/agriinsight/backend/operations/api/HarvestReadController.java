package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.HarvestQuery;
import com.agriinsight.backend.operations.application.HarvestService;
import com.agriinsight.backend.shared.api.ApiCommandResponses;
import com.agriinsight.backend.shared.api.ApiVersion;
import java.time.LocalDate;
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
@RequestMapping(ApiVersion.PREFIX + "/harvests")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class HarvestReadController {

    private final HarvestService harvests;

    public HarvestReadController(HarvestService harvests) {
        this.harvests = harvests;
    }

    @GetMapping
    HarvestPageResponse list(
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam Optional<UUID> farmId,
            @RequestParam Optional<UUID> fieldId,
            @RequestParam Optional<UUID> seasonId,
            @RequestParam Optional<UUID> cropId,
            @RequestParam Optional<LocalDate> occurredFrom,
            @RequestParam Optional<LocalDate> occurredTo) {
        return HarvestPageResponse.from(harvests.list(new HarvestQuery(
                limit, offset, farmId, fieldId, seasonId, cropId, occurredFrom, occurredTo)));
    }

    @GetMapping("/{id}")
    ResponseEntity<HarvestResponse> get(@PathVariable UUID id) {
        HarvestResponse response = HarvestResponse.from(harvests.get(id));
        return ResponseEntity.ok()
                .headers(ApiCommandResponses.headers(response.version()))
                .body(response);
    }
}
