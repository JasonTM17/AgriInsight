package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;

public interface TenantAuditReadStore {

    TenantAuditPage findAll(ScopeContext scope, TenantAuditQuery query);
}
