package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.CropQuery;
import com.agriinsight.backend.farm.application.CropService;
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
@RequestMapping(ApiVersion.PREFIX + "/crops")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CropReadController {

    private final CropService crops;

    public CropReadController(CropService crops) {
        this.crops = crops;
    }

    @GetMapping
    ResponseEntity<CropPageResponse> list(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = crops.list(new CropQuery(
                limit, offset, Optional.ofNullable(active), Optional.ofNullable(search)));
        return ResponseEntity.ok(CropPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<CropResponse> get(@PathVariable("id") UUID cropId) {
        CropResponse response = CropResponse.from(crops.get(cropId));
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }
}
