package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.InventoryReadService;
import com.agriinsight.backend.inventory.application.InventoryTransactionQuery;
import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
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
@RequestMapping(ApiVersion.PREFIX + "/inventory/transactions")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class InventoryTransactionReadController {

    private final InventoryReadService inventory;

    public InventoryTransactionReadController(InventoryReadService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    ResponseEntity<InventoryTransactionPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) InventoryTransactionKind kind,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo) {
        var page = inventory.listTransactions(new InventoryTransactionQuery(
                limit, offset, Optional.ofNullable(warehouseId), Optional.ofNullable(materialId),
                Optional.ofNullable(kind), Optional.ofNullable(occurredFrom),
                Optional.ofNullable(occurredTo)));
        return ResponseEntity.ok(InventoryTransactionPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<InventoryTransactionResponse> get(
            @PathVariable("id") UUID transactionId) {
        InventoryTransactionResponse response = InventoryTransactionResponse.from(
                inventory.getTransaction(transactionId));
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
