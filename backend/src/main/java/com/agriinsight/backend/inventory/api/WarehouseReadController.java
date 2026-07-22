package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.WarehouseQuery;
import com.agriinsight.backend.inventory.application.WarehouseService;
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
@RequestMapping(ApiVersion.PREFIX + "/warehouses")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class WarehouseReadController {

    private final WarehouseService warehouses;

    public WarehouseReadController(WarehouseService warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping
    ResponseEntity<WarehousePageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = warehouses.list(new WarehouseQuery(
                limit,
                offset,
                Optional.ofNullable(active),
                Optional.ofNullable(search)));
        return ResponseEntity.ok(WarehousePageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<WarehouseResponse> get(@PathVariable("id") UUID warehouseId) {
        WarehouseResponse response = WarehouseResponse.from(warehouses.get(warehouseId));
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
