package com.agriinsight.backend.realtime.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class RealtimeSummaryService {

    private final PermissionEvaluator permissions;
    private final RealtimeSummaryStore store;

    public RealtimeSummaryService(PermissionEvaluator permissions, RealtimeSummaryStore store) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.store = Objects.requireNonNull(store, "store is required");
    }

    public RealtimeSummary summarize() {
        return store.summarize(permissions.requireTenant(Permission.REALTIME_READ));
    }
}
