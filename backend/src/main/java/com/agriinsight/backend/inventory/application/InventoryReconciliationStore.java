package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;

public interface InventoryReconciliationStore {

    InventoryReconciliationReport reconcile(ScopeContext scope);
}
