package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.SupplierQuery;
import com.agriinsight.backend.inventory.application.SupplierService;
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
@RequestMapping(ApiVersion.PREFIX + "/suppliers")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class SupplierReadController {

    private final SupplierService suppliers;

    public SupplierReadController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    ResponseEntity<SupplierPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = suppliers.list(new SupplierQuery(
                limit,
                offset,
                Optional.ofNullable(active),
                Optional.ofNullable(search)));
        return ResponseEntity.ok(SupplierPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<SupplierResponse> get(@PathVariable("id") UUID supplierId) {
        SupplierResponse response = SupplierResponse.from(suppliers.get(supplierId));
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
