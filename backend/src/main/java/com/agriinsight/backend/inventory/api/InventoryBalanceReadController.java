package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.InventoryReadService;
import com.agriinsight.backend.inventory.application.StockBalanceQuery;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/inventory/balances")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class InventoryBalanceReadController {

    private final InventoryReadService inventory;

    public InventoryBalanceReadController(InventoryReadService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    ResponseEntity<StockBalancePageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) Boolean lowStock) {
        var page = inventory.listBalances(new StockBalanceQuery(
                limit, offset, Optional.ofNullable(warehouseId),
                Optional.ofNullable(materialId), Optional.ofNullable(lowStock)));
        return ResponseEntity.ok(StockBalancePageResponse.from(page));
    }
}
