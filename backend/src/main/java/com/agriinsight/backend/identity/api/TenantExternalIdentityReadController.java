package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.ExternalIdentityQuery;
import com.agriinsight.backend.identity.application.TenantExternalIdentityReadService;
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
@RequestMapping(ApiVersion.PREFIX + "/users/{id}/external-identities")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantExternalIdentityReadController {

    private final TenantExternalIdentityReadService identities;

    public TenantExternalIdentityReadController(TenantExternalIdentityReadService identities) {
        this.identities = identities;
    }

    @GetMapping
    ResponseEntity<ExternalIdentityPageResponse> list(
            @PathVariable("id") UUID profileId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ExternalIdentityPageResponse.from(identities.list(
                profileId,
                new ExternalIdentityQuery(
                        limit, offset, Optional.ofNullable(active)))));
    }
}
