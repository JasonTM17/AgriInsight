package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FieldQuery;
import com.agriinsight.backend.farm.application.FieldService;
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
@RequestMapping(ApiVersion.PREFIX + "/fields")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class FieldReadController {

    private final FieldService fields;

    public FieldReadController(FieldService fields) {
        this.fields = fields;
    }

    @GetMapping
    ResponseEntity<FieldPageResponse> list(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = fields.list(new FieldQuery(
                limit, offset, Optional.ofNullable(farmId),
                Optional.ofNullable(active), Optional.ofNullable(search)));
        return ResponseEntity.ok(FieldPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<FieldResponse> get(@PathVariable("id") UUID fieldId) {
        FieldResponse response = FieldResponse.from(fields.get(fieldId));
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }
}
