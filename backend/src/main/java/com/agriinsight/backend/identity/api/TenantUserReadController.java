package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.TenantUserQuery;
import com.agriinsight.backend.identity.application.TenantUserService;
import com.agriinsight.backend.shared.api.ApiVersion;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/users")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantUserReadController {

    private final TenantUserService tenantUsers;

    public TenantUserReadController(TenantUserService tenantUsers) {
        this.tenantUsers = tenantUsers;
    }

    @GetMapping
    ResponseEntity<TenantUserPageResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = tenantUsers.list(new TenantUserQuery(
                limit,
                offset,
                Optional.ofNullable(active),
                Optional.ofNullable(search)));
        return ResponseEntity.ok(TenantUserPageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<TenantUserResponse> get(@PathVariable("id") UUID profileId) {
        TenantUserResponse response = TenantUserResponse.from(tenantUsers.get(profileId));
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
