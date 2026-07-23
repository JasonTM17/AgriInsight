package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantRoleAssignmentQuery;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentService;
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
@RequestMapping(ApiVersion.PREFIX + "/users/{id}/roles")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantRoleReadController {

    private final TenantRoleAssignmentService roles;

    public TenantRoleReadController(TenantRoleAssignmentService roles) {
        this.roles = roles;
    }

    @GetMapping
    ResponseEntity<TenantRolePageResponse> list(
            @PathVariable("id") UUID profileId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(TenantRolePageResponse.from(roles.list(
                profileId,
                new TenantRoleAssignmentQuery(
                        limit, offset, Optional.ofNullable(active)))));
    }
}
