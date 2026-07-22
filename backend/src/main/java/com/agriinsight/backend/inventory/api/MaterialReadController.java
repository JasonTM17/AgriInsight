package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.MaterialQuery;
import com.agriinsight.backend.inventory.application.MaterialService;
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
@RequestMapping(ApiVersion.PREFIX + "/materials")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class MaterialReadController {

    private final MaterialService materials;

    public MaterialReadController(MaterialService materials) {
        this.materials = materials;
    }

    @GetMapping
    ResponseEntity<MaterialPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = materials.list(new MaterialQuery(
                limit,
                offset,
                Optional.ofNullable(active),
                Optional.ofNullable(search)));
        return ResponseEntity.ok(MaterialPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<MaterialResponse> get(@PathVariable("id") UUID materialId) {
        MaterialResponse response = MaterialResponse.from(materials.get(materialId));
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
